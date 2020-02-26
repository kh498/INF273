package no.uib.inf273

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import no.uib.inf273.processor.DataParser
import no.uib.inf273.processor.SolutionGenerator
import no.uib.inf273.search.LocalSearchA3
import no.uib.inf273.search.RandomSearch
import no.uib.inf273.search.Search
import no.uib.inf273.search.SimulatedAnnealingSearchA3
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.system.measureTimeMillis


class Main(
    parser: ArgParser
) {

    ////////////////////////
    // Required arguments //
    ////////////////////////

    val filePath: String by parser.storing(
        "-f",
        "--file",
        help = "Name of file to use."
    )

    ////////////////////////
    // Optional arguments //
    ////////////////////////

    private val logLevel by parser.mapping(
        "--info" to Logger.INFO,
        "--debug" to Logger.DEBUG,
        "--trace" to Logger.TRACE,
        help = "Logging level"
    ).default(Logger.INFO)

    val search: Search by parser.mapping(
        "--search-local-a3" to LocalSearchA3,
        "--sl3" to LocalSearchA3,
        "--search-random" to RandomSearch,
        "--sr" to RandomSearch,
        "--search-simulated-annealing-a3" to SimulatedAnnealingSearchA3,
        "--ssa3" to SimulatedAnnealingSearchA3,
        help = "What search method to use"
    ).default(Search.NoSearch)

    val seed: Long by parser.storing("The random seed to use") { toLong() }.default(Random.nextLong())

    val benchmark: Boolean by parser.flagging(
        "--benchmark-assignment-3",
        help = "Enable benchmarking as specified in Assignment 3. Search option will be ignored."
    ).default(false)

    val data: DataParser
    val solgen: SolutionGenerator

    val tune: Boolean by parser.flagging(
        "--tune",
        help = "Use and print the best calculated parameters. Note that a tuning is a VERY heavy operation and will take a long time."
    )

    init {
        log.logLevel = logLevel
        if (!benchmark && logLevel != Logger.INFO) search.log.logLevel = logLevel

        log.log("Random seed = $seed")
        rand = Random(seed)

        val content = readInternalFile(filePath)

        check(!content.isNullOrEmpty()) { "Failed to read file as it is null or empty" }

        data = DataParser(content)
        solgen = SolutionGenerator(data)

        if (benchmark) {
            val result = benchmarkA3()
            log.log { "Results for instance $filePath" }

            for ((alg, triple) in result) {
                printResults(alg, triple, true)
            }
        } else {
            require(search != Search.NoSearch) { "Search method must be specified when no other option is selected." }

            printResults(search, runAlgorithm(search, 10, solgen, tune), false)
        }
    }

    /**
     * Benchmark Local search, random search and simulated annealing as specified in assignment 3.
     *
     * Note that this is only done for the specified instance and not for all five.
     *
     * @return A map of the search mapping to average obj value, best obj val, then running time in ms
     */
    fun benchmarkA3(): Map<Search, Triple<Double, Long, Long>> {
        log.log { "Benchmark Assignment 3 " }
        val map: MutableMap<Search, Triple<Double, Long, Long>> = HashMap()

        for (search in listOf(RandomSearch, LocalSearchA3, SimulatedAnnealingSearchA3)) {
            log.log { "Running ${search.javaClass.simpleName}" }
            map[search] = runAlgorithm(search, 10, solgen, tune)
        }
        return map
    }

    fun printResults(search: Search, result: Triple<Double, Long, Long>, singleLine: Boolean) {

        val defaultObjVal = solgen.generateStandardSolution().objectiveValue(false).toDouble().toBigDecimal()
        val (avg, best, time) = result
        val improvement = 100.0.toBigDecimal() * (defaultObjVal - best.toBigDecimal()) / defaultObjVal

        if (singleLine) {
            log.log { "${search.javaClass.simpleName}, $avg, $best, $improvement%, $time ms" }
        } else {
            log.logs {
                listOf(
                    "Searching with algorithm ${search.javaClass}"
                    , "initial obj val $defaultObjVal"
                    , "Best obj value  $best"
                    , "avg obj value . $avg"
                    , "Improvement . . $improvement%"
                    , "Time  . . . . . $time ms"
                )
            }
        }
    }

    companion object {


        /**
         * Global logger
         */
        val log = Logger()

        lateinit var rand: Random

        /**
         * Read an internal file to text
         *
         * @return The content of the file or `null` if the file cannot be read
         */
        fun readInternalFile(path: String): String? {
            return Main::class.java.classLoader.getResource(path)?.readText()
        }

        /**
         * Run an algorithm [samples] times and report back results.
         *
         * @return A triple with values in order: average objective value, best objective value, time takes in milliseconds
         */
        fun runAlgorithm(
            search: Search,
            samples: Int,
            solgen: SolutionGenerator,
            tune: Boolean
        ): Triple<Double, Long, Long> {
            log.debug { "Running algorithm ${search.javaClass.simpleName}" }
            var totalObj = BigDecimal.ZERO
            var bestObj = Long.MAX_VALUE
            var times = 0L

            if (tune) {
                search.tune(solgen, samples, true)
            }

            for (i in 0 until samples) {
                var sol0: Solution? = null
                val time = measureTimeMillis {
                    sol0 = search.search(solgen.generateStandardSolution())
                }
                val sol: Solution = sol0!!
                times += time

                check(sol.isFeasible(true)) {
                    "returned solution (using ${search.javaClass.simpleName}) is not feasible: ${sol.arr.contentToString()}"
                }
                val objVal = sol.objectiveValue(true)
                totalObj += objVal.toBigDecimal()
                if (objVal < bestObj) {
                    bestObj = objVal
                }
            }

            return Triple((totalObj / samples.toBigDecimal()).toDouble(), bestObj, times / samples)
        }
    }
}

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::Main).run { }
}


