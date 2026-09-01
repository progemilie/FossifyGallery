package org.fossify.gallery.helpers

import android.os.Debug
import android.os.Trace
import org.fossify.gallery.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * What the app did, how often, and for how long. Debug builds only: [ENABLED] is a build-type
 * literal, so the compiler folds away the body of every call below - and, [count] and [section]
 * being inline, the call itself - before R8 ever sees it. The class stays in a release APK
 * regardless, because proguard-rules.pro keeps all of `org.fossify.**`; nothing calls into it.
 *
 * Two things this answers that a method trace does not. A trace records every call, which costs
 * about ten times the runtime to collect and so moves the very timings it reports; a counter costs
 * a few nanoseconds, leaving the interaction behaving like itself. And a counter names *work* -
 * "a thumbnail was decoded" - which survives the code moving to another class, where a method name
 * does not.
 *
 * Read them with `perf.py counters`. Sections are handed to atrace as well, so the same names show
 * up as slices in a Perfetto trace (`perf.py trace`) beside the frames they delayed.
 */
object Perf {

    const val ENABLED = BuildConfig.PERF_COUNTERS

    private const val NS_PER_MS = 1e6
    private const val NS_PER_US = 1e3
    private const val BYTES_PER_MB = 1e6
    private const val NAME_WIDTH = 34

    private val counters = ConcurrentHashMap<String, Counter>()

    private fun counterOf(name: String) = counters.getOrPut(name) { Counter() }

    class Counter {
        val calls = AtomicLong()
        val totalNs = AtomicLong()
        val maxNs = AtomicLong()
    }

    /** One occurrence of something worth counting but not worth timing. */
    @Suppress("NOTHING_TO_INLINE") // inline so the call vanishes at the call site, not just its body
    inline fun count(name: String) {
        if (ENABLED) {
            record(name, 0L)
        }
    }

    /** Times [block] and counts it under [name]. */
    inline fun <T> section(name: String, block: () -> T): T {
        if (!ENABLED) {
            return block()
        }
        val startedAt = System.nanoTime()
        // nothing may sit between this and the try, or the section leaks when block() throws
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
            record(name, System.nanoTime() - startedAt)
        }
    }

    /** Public only because [section] is inline, and an inline body cannot reach anything else. */
    fun record(name: String, elapsedNs: Long) {
        val counter = counterOf(name)
        counter.calls.incrementAndGet()
        counter.totalNs.addAndGet(elapsedNs)
        // a compare-and-set loop rather than accumulateAndGet, whose operator is allocated
        // afresh on every call -- 38 needless objects per fling, from the profiler itself
        var seen = counter.maxNs.get()
        while (elapsedNs > seen && !counter.maxNs.compareAndSet(seen, elapsedNs)) {
            seen = counter.maxNs.get()
        }
    }

    fun reset() = counters.clear()

    /** The table `perf.py counters` prints, heaviest first. */
    fun report(): String {
        val runtime = Runtime.getRuntime()
        val heading = "%-${NAME_WIDTH}s %8s %10s %9s %9s"
            .format("what", "calls", "total ms", "avg us", "max us")
        val rows = counters.entries
            .sortedByDescending { it.value.totalNs.get() }
            .map { (name, c) ->
                val calls = c.calls.get()
                val total = c.totalNs.get()
                "%-${NAME_WIDTH}s %8d %10.1f %9.0f %9.0f".format(
                    name.take(NAME_WIDTH), calls, total / NS_PER_MS,
                    if (calls > 0) total / NS_PER_US / calls else 0.0,
                    c.maxNs.get() / NS_PER_US
                )
            }
        val memory = "java heap %.1f MB of %.1f MB, native %.1f MB".format(
            (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            runtime.maxMemory() / BYTES_PER_MB,
            Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB
        )
        return if (rows.isEmpty()) {
            "no counters yet\n$memory"
        } else {
            (listOf(heading) + rows + listOf(memory)).joinToString("\n")
        }
    }
}
