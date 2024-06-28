package helpers

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

class JobProducer(private val f: suspend () -> Unit, val interval: Duration, val initialDelay: Long?) {
    suspend operator fun invoke() {
        f()
    }
}

class JobProducerScheduler(val service: JobProducer) : CoroutineScope {
    private val job = Job()

    private val singleThreadExecutor = Executors.newSingleThreadExecutor()

    override val coroutineContext: CoroutineContext
        get() = job + singleThreadExecutor.asCoroutineDispatcher()


    fun stop() {
        job.cancel()
        singleThreadExecutor.shutdown()
    }

    fun start() = launch {
        service.initialDelay?.let {
            delay(it)
        }
        while (isActive) {
            service()
            delay(service.interval)
        }
        println("Scheduled job coroutine done")
    }
}