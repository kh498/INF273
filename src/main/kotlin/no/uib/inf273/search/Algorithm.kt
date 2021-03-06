@file:Suppress("LeakingThis")

package no.uib.inf273.search

import no.uib.inf273.Logger
import no.uib.inf273.processor.Solution
import no.uib.inf273.processor.SolutionGenerator

abstract class Algorithm {
    val log: Logger = Logger(toString())

    /**
     * Search with the given search algorithm, no guarantee is given towards the initial solution [sol] being modified
     *
     * @throws IllegalArgumentException If [iterations] is a non-positive number
     */
    abstract fun search(sol: Solution, iterations: Int = 10_000): Solution

    /**
     * Tune the given [solgen] for [iterations] number of iterations.
     *
     * If [report] is `true` this method will print out extra information about the tuned parameters
     */
    abstract fun tune(solgen: SolutionGenerator, iterations: Int, report: Boolean)

    open fun updateLogLevel(level: Int) {
        log.logLevel = level
    }

    override fun toString(): String {
        return this::class.java.simpleName
    }

    object NoAlgorithm : Algorithm() {

        override fun search(sol: Solution, iterations: Int): Solution {
            error("Cannot use the No Algorithm object to search. This is just a dummy implementation")
        }

        override fun tune(solgen: SolutionGenerator, iterations: Int, report: Boolean) {
            error("Cannot use the No Algorithm object to search. This is just a dummy implementation")
        }
    }
}
