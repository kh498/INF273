package no.uib.inf273.search.given.simulatedAnnealing

import no.uib.inf273.operators.given.ReinsertOnceOperator
import no.uib.inf273.operators.given.ThreeExchangeOperator
import no.uib.inf273.operators.given.TwoExchangeOperator

object SimulatedAnnealingAlgorithmA3 : SimulatedAnnealingAlgorithm(
    0.001 to TwoExchangeOperator,
    0.05 to ThreeExchangeOperator,
    fallbackOp = ReinsertOnceOperator.INST
) {
    override fun toString(): String {
        return "Simulated Annealing A3"
    }
}
