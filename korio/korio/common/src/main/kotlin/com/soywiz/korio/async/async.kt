@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package com.soywiz.korio.async

import com.soywiz.korio.*
import com.soywiz.korio.coroutine.*
import com.soywiz.korio.error.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.util.*
import kotlin.coroutines.experimental.*

suspend inline fun <T> suspendCoroutineEL(crossinline block: (Continuation<T>) -> Unit): T =
	_korioSuspendCoroutine { c ->
		block(c.toEventLoop())
	}

fun <T> Continuation<T>.toEventLoop(): Continuation<T> {
	val parent = this
	return object : Continuation<T> {
		override val context: CoroutineContext = parent.context
		override fun resume(value: T): Unit = run {
			context.eventLoop.queueContinuation(parent, value)
		}

		override fun resumeWithException(exception: Throwable): Unit = run {
			context.eventLoop.queueContinuationException(parent, ExceptionHook.hook(exception))
		}
	}
}

interface CheckRunning {
	val coroutineContext: CoroutineContext
	val cancelled: Boolean
	fun checkCancelled(): Unit
}

val CheckRunning.eventLoop get() = coroutineContext.eventLoop

suspend fun parallel(vararg tasks: suspend () -> Unit) {
	tasks.map { go(getCoroutineContext(), it) }.await()
}

fun <T> spawn(context: CoroutineContext, task: suspend () -> T): Promise<T> {
	val deferred = Promise.Deferred<T>()
	task.korioStartCoroutine(deferred.toContinuation(context))
	return deferred.promise
}

suspend fun <T> spawn(task: suspend () -> T): Promise<T> {
	val deferred = Promise.Deferred<T>()
	task.korioStartCoroutine(deferred.toContinuation(getCoroutineContext()))
	return deferred.promise
}

interface CoroutineContextHolder {
	val coroutineContext: CoroutineContext
}


// Aliases for spawn
fun <T> async(context: CoroutineContext, task: suspend () -> T): Promise<T> = spawn(context, task)

fun <T> go(context: CoroutineContext, task: suspend () -> T): Promise<T> = spawn(context, task)

fun <T> CoroutineContextHolder.go(task: suspend () -> T): Promise<T> = spawn(this.coroutineContext, task)
fun <T> CoroutineContextHolder.async(task: suspend () -> T): Promise<T> = spawn(this.coroutineContext, task)
fun <T> CoroutineContextHolder.spawn(task: suspend () -> T): Promise<T> = spawn(this.coroutineContext, task)

suspend fun <T> async2(task: suspend () -> T): Promise<T> = spawn { task() }

suspend fun <T> async(task: suspend CoroutineContext.() -> T): Promise<T> = spawn { task(getCoroutineContext()) }
suspend fun <T> go(task: suspend CoroutineContext.() -> T): Promise<T> = spawn { task(getCoroutineContext()) }

fun <T> EventLoop.async(task: suspend CoroutineContext.() -> T): Promise<T> =
	spawn(this@async.coroutineContext) { task(this@async.coroutineContext) }

fun <T> CoroutineContext.async(task: suspend CoroutineContext.() -> T): Promise<T> =
	spawn(this@async) { task(this@async) }

fun <T> EventLoop.go(task: suspend CoroutineContext.() -> T): Promise<T> =
	spawn(this@go.coroutineContext) { task(this@go.coroutineContext) }

fun <T> CoroutineContext.go(task: suspend CoroutineContext.() -> T): Promise<T> = spawn(this@go) { task(this@go) }

suspend fun <R, T> (suspend R.() -> T).await(receiver: R): T = korioSuspendCoroutine { c ->
	this.korioStartCoroutine(receiver, c)
}

suspend fun <T> (suspend () -> T).await(): T = korioSuspendCoroutine { c ->
	this.korioStartCoroutine(c)
}

fun <R, T> (suspend R.() -> T).execAndForget(context: CoroutineContext, receiver: R) = spawnAndForget(context) {
	this.await(receiver)
}

fun <T> (suspend () -> T).execAndForget(context: CoroutineContext) = spawnAndForget(context) {
	this.await()
}


class EmptyContinuation(override val context: CoroutineContext) : Continuation<Any> {
	override fun resume(value: Any) = Unit
	override fun resumeWithException(exception: Throwable) = ExceptionHook.hook(exception).printStackTrace()
}


@Suppress("UNCHECKED_CAST")
inline fun <T> spawnAndForget(context: CoroutineContext, noinline task: suspend () -> T): Unit =
	task.korioStartCoroutine(EmptyContinuation(context) as Continuation<T>)

suspend fun <T> spawnAndForget(task: suspend () -> T): Unit = spawnAndForget(getCoroutineContext(), task)

inline fun <T> spawnAndForget(context: CoroutineContext, value: T, noinline task: suspend T.() -> Any): Unit =
	task.korioStartCoroutine(value, EmptyContinuation(context))

//fun syncTest(callback: suspend EventLoopTest.() -> Unit): Unit = TODO()

fun <T : Any> ioSync(callback: suspend () -> T): T {
	var completed = false
	lateinit var result: T
	var resultEx: Throwable? = null
	callback.startCoroutine(object : Continuation<T> {
		override val context: CoroutineContext = EmptyCoroutineContext
		override fun resume(value: T) = run { result = value; completed = true }
		override fun resumeWithException(exception: Throwable) = run { resultEx = exception; completed = true }
	})
	if (!completed) invalidOp("ioSync was not completed synchronously!")
	if (resultEx != null) throw resultEx!!
	return result
}

fun runBlocking(block: suspend EventLoopTest.() -> Unit): Unit = KorioNative.syncTest(block)

fun suspendTest(block: suspend EventLoopTest.() -> Unit): Unit = KorioNative.syncTest(block)
fun suspendTestIgnoreJs(block: suspend EventLoopTest.() -> Unit): Unit {
	if (!OS.isJs) {
		KorioNative.syncTest(block)
	}
}

fun <TEventLoop : EventLoop> sync(el: TEventLoop, step: Int = 10, block: suspend TEventLoop.() -> Unit): Unit {
	if (OS.isJs) throw UnsupportedOperationException("sync block is not supported on javascript target. It is intended for testing.")
	var result: Any? = null

	tasksInProgress.incrementAndGet()
	block.korioStartCoroutine(el, object : Continuation<Unit> {
		override val context: CoroutineContext = el.coroutineContext

		override fun resume(value: Unit) = run {
			tasksInProgress.decrementAndGet()
			result = value
		}

		override fun resumeWithException(exception: Throwable) = run {
			tasksInProgress.decrementAndGet()
			val e = ExceptionHook.hook(exception)
			result = e
		}
	})

	while (result == null) {
		Thread_sleep(1L)
		el.step(step)
	}

	if (result is Throwable) throw result as Throwable
	return Unit
}

/*
// Wait for a suspension block for testing purposes
fun <T> sync(block: suspend () -> T): T {
	if (OS.isJs) throw UnsupportedOperationException("sync block is not supported on javascript target. It is intended for testing.")
	var result: Any? = null

	val el = eventLoopFactoryDefaultImpl.createEventLoop()
	tasksInProgress.incrementAndGet()
	block.korioStartCoroutine(object : Continuation<T> {
		override val context: CoroutineContext = CoroutineCancelContext() + EventLoopCoroutineContext(el)

		override fun resume(value: T) = run {
			tasksInProgress.decrementAndGet()
			result = value
		}

		override fun resumeWithException(exception: Throwable) = run {
			val e = ExceptionHook.hook(exception)
			tasksInProgress.decrementAndGet()
			result = e
		}
	})

	while (result == null) Thread_sleep(1L)
	if (result is Throwable) throw result as Throwable
	@Suppress("UNCHECKED_CAST")
	return result as T
}
*/

fun Thread_sleep(time: Long): Unit = KorioNative.Thread_sleep(time)
fun sleepBlocking(time: Long): Unit = KorioNative.Thread_sleep(time)
