package no.uib.inf273.search.given

import no.uib.inf273.Main.Companion.rand
import no.uib.inf273.operators.given.ReinsertOnceOperator
import no.uib.inf273.operators.given.ThreeExchangeOperator
import no.uib.inf273.operators.given.TwoExchangeOperator
import no.uib.inf273.processor.Solution
import no.uib.inf273.processor.SolutionGenerator
import no.uib.inf273.search.Algorithm

/**
 * Modified local search for assignment 3
 */
object LocalAlgorithmA3 : Algorithm() {

    var p1: Float = 0.4f
    var p2: Float = 0.5f

    override fun search(sol: Solution, iterations: Int): Solution {
        require(0 <= p1 && p1 < p2 && p2 + p1 < 1) {
            "Invalid probabilities. They must be in acceding order and in range [0,1). | p1=$p1, p2=$p2"
        }
        require(0 < iterations) { "Iteration must be a positive number" }

        //Best known solution
        val best = sol.copy()
        //objective value of the best known solution
        var bestObjVal = best.objectiveValue(false)

        //current solution
        val curr = sol
        var currObjVal: Long


        for (i in 0 until iterations) {
            val rsi = rand.nextFloat()
            val op = when {
                rsi < p1 -> TwoExchangeOperator
                rsi < p1 + p2 -> ThreeExchangeOperator
                else -> ReinsertOnceOperator.INST
            }

            log.trace { "Using op ${op.javaClass.simpleName}" }

            //copy the best solution to the current solution
            // this avoids allocating new objects or memory
            best.arr.copyInto(curr.arr)

            op.operate(curr)

            currObjVal = curr.objectiveValue(false)

            if (currObjVal < bestObjVal) {
                log.debug { "New best answer ${best.arr.contentToString()} with objective value $currObjVal. Diff is  ${currObjVal - bestObjVal} " }
                curr.arr.copyInto(best.arr)
                bestObjVal = currObjVal
            }
        }
        return best
    }


    override fun tune(solgen: SolutionGenerator, iterations: Int, report: Boolean) {
        //TODO("Tune percentages p1 and p2")
    }
}
