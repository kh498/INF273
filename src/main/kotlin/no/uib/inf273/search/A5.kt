package no.uib.inf273.search

import no.uib.inf273.Main.Companion.rand
import no.uib.inf273.extra.mapValuesInPlace
import no.uib.inf273.operators.MinimizeNotTransported
import no.uib.inf273.operators.MinimizeWaitTime
import no.uib.inf273.operators.MoveSimilarCargo
import no.uib.inf273.operators.Operator
import no.uib.inf273.operators.escape.EscapeOperator
import no.uib.inf273.operators.escape.MoveToSpotCarrierOperator
import no.uib.inf273.operators.escape.ReinsertNOperator
import no.uib.inf273.operators.given.ReinsertOnceOperator
import no.uib.inf273.operators.given.ThreeExchangeOperator
import no.uib.inf273.processor.Solution
import no.uib.inf273.processor.SolutionGenerator
import no.uib.inf273.search.A5.OperatorCharacteristic
import no.uib.inf273.search.A5.OperatorCharacteristic.*
import no.uib.inf273.search.given.simulatedAnnealing.SimulatedAnnealingAlgorithm
import java.lang.Integer.max
import kotlin.math.exp

/**
 * An algorithm based an Simulated Annealing, Tabu, and performance of operators
 *
 * ## Description of the algorithm
 *
 * A very high level pseudo code of the algorithm is as follows
 *
 * ```
 * S -> Set of operators
 * W -> Weight of operator (in same iteration order as S)
 * I -> number of iterations
 * B -> Global best solution
 * C -> Current solution
 * T -> Current temperature
 * L -> List of taboo results
 * J -> number of iteration without an improvement
 *
 * for iter_nr in 0..I:
 *     if iter_nr mod (1% of I) is 0:
 *         Recalculating the operator weights and reset all operator point counters
 *
 *     #Use taboo length to try and reduce bad runs early
 *     if J >= 0.5% of I:
 *          Reduce size of L
 *
 *     if J mod (2% of I) is 0:
 *          O' <- Select an escape operator
 *          C <- Operate on C with selected operator O'
 *
 *     O <- Select operator based on weights W
 *     N <- Operate on a copy of C with selected operator O
 *     ∆E <- objective value of N - objective value of C
 *
 *     if N is feasible:
 *         # This part is simulated annealing with taboo check
 *         if ∆E < 0 and N is not in L:
 *             Set C to be N and update L
 *             if objective value of N < objective value of B
 *                 Set B to be N
 *         else if rand(0d..1d) < e ^ (-∆E / T):
 *             Set C to be N and update L
 *
 *     Calculate points to give to O based on N
 *     Update J and reset length of L if C was updated
 *     Update T
 * ```
 *
 * ## Automatic weighing of the operators
 *
 * The given operators used are automatically weighted based on their performance in the last iteration segment.
 *
 * ### Initial Weight & Operator Characteristics
 *
 * Each operator will with no other specification be given equal weights.
 * However for each operator it is possible to specify characteristics that will modify how
 * the weights will be calculated.
 *
 * see [OperatorCharacteristic]
 *
 * ### Iteration Segment
 *
 * An iteration segment is defined as lasting for 1% of the total iterations.
 * In other words there are in total 100 iteration segments each search.
 *
 * The reason to use a percentage and not a fixed number to let the algorithm scale to the number of iterations.
 * Having more iterations to gather the weights to be used is seen as a positive element.
 * It also allow the algorithm to scale down the number of iterations if a quick search is needed for what ever reason.
 *
 * ### Operator Scoring System
 *
 * For all conditions below change the score accordingly to their weight.
 *
 * * Global best solution . += 1.00 pt (Greater weight if a new global best is found to encourage this)
 * * Better solution. . . . += 0.50 pt
 * * Feasible solution. . . += 0.50 pt
 * * Infeasible solution. . -= 0.50 pt
 * * Taboo solution . . . . -= 0.25 pt (Discourage taboo solutions, note that C does not count as taboo)
 * * Worse solution . . . . -= 0.25 pt (At least it is feasible)
 *
 * ### Examples
 *
 * * A new global best solution : 1.00 + 0.50 + 0.50 = 2.0
 * * Better solution            : 0.50 + 0.50 = 1.0
 * * Worse Taboo infeasible     : -0.25 - 0.25 -0.50 = -1.00 (worst case)
 *
 *  ### Calculating the new Weights
 *
 *  Each operator have a unbiased weight score `u` equal to `max(0, total points scored in the last segment)` divided
 *  by the total time the operator was selected. The old weight from the previous segment is called `o`, initially it
 *  is specified by the operator characteristic,`modifier` is the operator characteristic modifier, and `n` is the sum of the new operator weight.
 *
 *  The new relative weight `r` is `(o/2 + u * modifier) / n`.
 *
 * ## Taboo Solutions
 *
 * There should be a small cache that disallows recently seen solution (like in Tabu search) use the hashed value of the solution array.
 * The cache should be a double linked set of determined size. When a new (non-taboo) solution is should the hash of this solution
 * is added to the front of the list and the tail of the list is removed, only if the size of the list is greater than the
 * determined size. If the determined size of the list is changed to a smaller size the tail of the list is removed.
 *
 * ### Taboo Size
 *
 * The size of the taboo list should be dynamic should be at least `max(1, 0.001% of total iterations)` and less
 * than `max(minimum taboo size + 1, 0.01% of total iterations)`.
 * The size should normally be at max length, as specified above, but should be shortened when there are no new solutions found for a while.
 *
 * ### Hash Collisions
 *
 * There should not be any hash duplicates in the taboo set. If a duplicate is being added it should be added in
 * such a way that it is the youngest (ie first) member of the set. This can be done by removing then adding the element.
 * It might be added because two different solutions gives the same hash or it might be added due to the boltzmann probability.
 *
 * ## Escape operator
 *
 * Escape operators are operator intended to do large changes to get the solution out of a local optima. They should
 * drastically change the solution in the hope that the solution will escape the current local optima.
 *
 * @author Elg
 */
object A5 : Algorithm() {

    /**
     * percent each segment takes up
     */
    private const val SEGMENT_PERCENT = 0.01

    /**
     * Percentage of total iterators needed for the taboo list length to be reduced
     */
    private const val TABOO_REDUCTION_THRESHOLD_PERCENT = 0.005

    /**
     * Percentage of total iterators needed for escape operators to be used
     */
    private const val ESCAPE_THRESHOLD_PERCENT = 0.02

    /**
     * Minimum length of the taboo list in percent of total iterations.
     *
     * Note that it is in reality `max(1, `[MIN_TABOO_SIZE_PERCENT]`% of total iterations)`
     */
    private const val MIN_TABOO_SIZE_PERCENT = 0.001

    /**
     * Maximum length of taboo list in percent of total iterations
     *
     * Note that it is in reality `max(max(1, `[MIN_TABOO_SIZE_PERCENT]`% of total iterations) + 1, `[MAX_TABOO_SIZE_PERCENT]`% of total iterations)`
     */
    private const val MAX_TABOO_SIZE_PERCENT = 0.01

    ////////////
    // Scores //
    ////////////

    /*
 * * Global best solution . += 1.00 pt (Greater weight if a new global best is found to encourage this)
 * * Better solution. . . . += 0.50 pt
 * * Identical solution . . += 0.50 pt (No change have was made)
 * * Feasible solution. . . += 0.50 pt
 * * Infeasible solution. . -= 0.50 pt
 * * Taboo solution . . . . -= 0.25 pt (Discourage taboo solutions, note that C does not count as taboo)
 * * Worse solution . . . . -= 0.25 pt (At least it is feasible)*/

    private const val GLOBAL_BEST_SCORE = 1.0
    private const val BETTER_SCORE = 0.5
    private const val FEASIBLE_SCORE = 0.5
    private const val INFEASIBLE_SCORE = -0.5
    private const val TABOO_SCORE = -0.25
    private const val WORSE_SCORE = -0.25

    /**
     * Operators to be used in this algorithm together with what characteristic they have
     */
    private val ops: Map<Operator, OperatorCharacteristic> = mapOf(
        MinimizeNotTransported to EARLY,
        MinimizeWaitTime to NOTHING,
        MoveSimilarCargo to NOTHING,
        ReinsertOnceOperator(0.75) to NOTHING,
        ThreeExchangeOperator to LATE
    )

    /**
     * The escape operators to be used when stuck in a local optima
     */
    private val escapeOps: Array<EscapeOperator> = arrayOf(MoveToSpotCarrierOperator, ReinsertNOperator)

    override fun search(sol: Solution, iterations: Int): Solution {

        ///////////////
        // Constants //
        ///////////////

        val iterPerSegment: Int = (iterations * SEGMENT_PERCENT).toInt()
        val escapeThreshold = (iterations * ESCAPE_THRESHOLD_PERCENT).toInt()
        val tabooSizeReductionThreshold = (iterations * TABOO_REDUCTION_THRESHOLD_PERCENT).toInt()
        val taboo = Taboo(iterations)

        log.debug { "Each segment lasts $iterPerSegment iterations" }
        log.debug { "Size of taboo solutions will be reduced after $tabooSizeReductionThreshold iterations" }
        log.debug { "Escape operator will be applied every $escapeThreshold iterations of non-improvement" }

        ///////////////
        // Variables //
        ///////////////

        val bestSol = sol.copy()
        var bestObjVal = bestSol.objectiveValue(false)

        val currSol = sol.copy()
        var currObjVal = currSol.objectiveValue(false)

        /**
         * Current weight of each operator. The sum of all weights must be 1.0
         *
         */
        val weights = HashMap<Operator, Double>().apply {
            val acc = ops.values.map { it.initialWeight }.sum()

            this.putAll(ops.map { (op, characteristic) ->
                //used to normalize weight
                op to characteristic.initialWeight / acc
            })
        }

        /**
         * search weight to allow to find an operator easily
         */
        val searchWeights = ArrayList<Pair<Operator, Double>>()


        /**
         * Current score of each operator together with how many times they have been selected
         */
        val segmentScore = HashMap<Operator, Pair<Double, Int>>().also { map ->
            map.putAll(ops.keys.map { it to Pair(0.0, 0) })
        }

        /**
         * Number of iterations without an improvement found
         */
        var nonImprovementIteration = 0

        ///////////////////////
        // Internal function //
        ///////////////////////

        fun findOperator(): Operator {
            val percent: Double = rand.nextDouble()
            for ((op, prob) in searchWeights) {
                if (percent < prob) {
                    return op
                }
            }
            return searchWeights.last().first
        }

        fun recalculateSearchWeights() {
            var acc = 0.0
            searchWeights.clear()
            for ((op, weight) in weights) {
                searchWeights += op to weight + acc
                acc += weight
            }
        }

        //call it at once as we operate a bit when calculating best temperature
        recalculateSearchWeights()

        log.debug { "initial weights $weights" }
        log.debug { "initial search weights $searchWeights" }

        /////////////////
        // Temperature //
        /////////////////


        val (initTemperature, coolingFactor) = SimulatedAnnealingAlgorithm.calculateParameters(
            sol = sol,
            iterations = iterations,
            testRuns = 100
        ) { findOperator().operate(it) }

        log.debugs {
            listOf(
                "Initial temperature $initTemperature",
                "Cooling factor $coolingFactor"
            )
        }

        /**
         * Current temperature
         */
        var temperature = initTemperature

        /**
         * Calculate the probability of accepting a worse solution
         */
        fun boltzmannProbability(deltaE: Long): Boolean {
            return rand.nextDouble() < exp(-deltaE / temperature)
        }


        ///////////////
        // Main loop //
        ///////////////

        for (i in 1..iterations) {
//          if iter_nr mod (1% of I) is 0:
            if (i % iterPerSegment == 0) {
//              Recalculating the operator weights and reset all operator point
                log.debug { "End of segment (i: $i)" }

                //(o / 2 + u * modifier) / n

                for ((op, oldWeight) in weights.toList()) {
                    val (score, times) = segmentScore[op] ?: error("Failed to find op ($op) in segment score list")
                    val relativeScore = (score / times)
                    val characteristic = ops[op] ?: error("Failed to find op ($op) in list of ops")

                    weights[op] = oldWeight / 2 + characteristic.modifier(relativeScore)
                }

                val n = weights.values.sum()
                weights.mapValuesInPlace { (_, weight) -> weight / n }

                recalculateSearchWeights()
                //reset all scores
                segmentScore.mapValuesInPlace { Pair(0.0, 0) }
                continue
            }

//          if J >= 0.5% of I:
            if (nonImprovementIteration >= tabooSizeReductionThreshold) {
//              Reduce size of L
                taboo.reduceSize()
            }

//          if J mod (2% of I) is 0:
            if (nonImprovementIteration % escapeThreshold == 0) {
//              O' <- Select an escape operator
                val escOp = escapeOps.random(rand)
//              C <- Operate on C with selected operator O'
                //operate directly on the current solution
                escOp.operate(currSol)
            }

//          O <- Select operator based on weights W
            val op = findOperator()

            var (opScore, amountSel) = segmentScore[op] ?: error("Operator $op not in segment scores")

//          N <- Operate on C a copy of with selected operator O
            val newSol = currSol.copy()
            op.operate(newSol)


            val newSolObjVal = newSol.objectiveValue(false)
            val isTaboo = taboo.checkTaboo(newSol)

            if (isTaboo) {
                opScore += TABOO_SCORE
            }

//          ∆E <- objective value of N - objective value of C
            val deltaE = newSolObjVal - currObjVal

            opScore += if (deltaE < 0) BETTER_SCORE else WORSE_SCORE

//          if N is feasible:
            if (newSol.isFeasible(modified = true)) {

                opScore += FEASIBLE_SCORE
//              if ∆E < 0 and N is not in L:
                if (deltaE < 0 && !isTaboo) {

                    //increase size of taboo !
                    //TODO maybe increase by 1 instead of not to max?
                    nonImprovementIteration = 0
                    taboo.resetSize()

//                  Set C to be N and update L
                    newSol.copyInto(currSol)
                    currObjVal = newSolObjVal
                    taboo.push(newSol)

//                  if objective value of N < objective value of B
                    if (newSolObjVal < bestObjVal) {
                        opScore += GLOBAL_BEST_SCORE
//                      Set B to be N
                        newSol.copyInto(bestSol)
                        bestObjVal = newSolObjVal
                    }

//              else if rand(0d..1d) < e ^ (-∆E / T):
                } else if (boltzmannProbability(deltaE)) {
//                  Set C to be N and update L
                    newSol.copyInto(currSol)
                    currObjVal = newSolObjVal
                    taboo.push(newSol)

//                  Update J and reset length of L if C was updated
                    nonImprovementIteration++
                } else {
//                  Update J and reset length of L if C was updated
                    nonImprovementIteration++
                }
            } else {
                opScore += INFEASIBLE_SCORE
            }
//          Calculate points to give to O based on N
            segmentScore[op] = opScore to amountSel + 1

//          Update T
            temperature *= coolingFactor
        }
        require(bestSol.isFeasible(true, true))
        return bestSol
    }

    override fun tune(solgen: SolutionGenerator, iterations: Int, report: Boolean) {
        TODO("not implemented")
    }

    /**
     * @param initialWeight What the initial weight should be of an operator with this Characteristic
     * @param modifier A function that return a number between `0` and `1` to be multiplied with the calculated weight.  The argument given to the function is a number between 0 and 1 showing percentage of iterations completed.
     */
    enum class OperatorCharacteristic(
        val initialWeight: Double,
        val modifier: (progress: Double) -> Double
    ) {

        /**
         * No characteristic is known about the given operator.
         *
         * This is the default option
         *
         */
        NOTHING(1.0, { 1.0 }),

        /**
         * The operator is good __early__ in the search
         */
        EARLY(1.25, { progress: Double ->
            if (progress <= 0.25) 1.25 - progress else 0.9
        }),

        /**
         * The operator is good __late__ in the search
         */
        LATE(1.0, { progress: Double ->
            if (progress <= 0.25) 1.0 else 1.0 + progress / 2
        })
    }

    class Taboo(iterations: Int) {

        val minTabooSize = max(1, (iterations * MIN_TABOO_SIZE_PERCENT).toInt())
        val maxTabooSize = max(minTabooSize + 1, (iterations * MAX_TABOO_SIZE_PERCENT).toInt())

        init {
            log.debug { "There can be between (inc) $minTabooSize and $maxTabooSize (ex) taboos registered" }
        }

        /**
         * Currently maximum allowed taboo size
         */
        var currentMaxTabooSize = maxTabooSize
            private set

        /**
         * All current taboo solutions hashes
         */
        private val taboo = LinkedHashSet<Int>(currentMaxTabooSize)

        fun resetSize() {
            currentMaxTabooSize = maxTabooSize
        }

        fun reduceSize() {
            currentMaxTabooSize = (currentMaxTabooSize - 1).coerceAtLeast(minTabooSize)
            checkSize()
        }

        /**
         * Make sure the list is always within range
         */
        private fun checkSize() {
            var excess = taboo.size - currentMaxTabooSize
            if (excess > 0) {
                val iter = taboo.iterator()
                while (excess > 0 && iter.hasNext()) {
                    iter.next()
                    iter.remove()
                    excess--
                }
            }
            check(taboo.size <= currentMaxTabooSize)
        }

        /**
         * Add a new solution to the taboo
         */
        fun push(sol: Solution) {
            val hash = sol.hashCode()

            //remove if already containing it
            // to make sure the iteration order is correct
            if (taboo.contains(hash)) {
                taboo.remove(hash)
            }

            taboo.add(hash)
            checkSize()
        }

        /**
         * Check if a solution is taboo
         */
        fun checkTaboo(sol: Solution): Boolean {
            return taboo.contains(sol.hashCode())
        }
    }
}
