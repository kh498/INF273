package no.uib.inf273.operators

import no.uib.inf273.Logger
import no.uib.inf273.Main
import no.uib.inf273.extra.filter
import no.uib.inf273.extra.insert
import no.uib.inf273.processor.Solution

/**
 * An operator that picks two unique vessels (including freights) then moves cargo from the origin vessel to the destination vessel.
 */
object ReinsertOnceOperator : Operator {


    override val log = Logger()

    override fun operate(sol: Solution) {
        val sub = sol.splitToSubArray(true)

        //select two vessels where the origin vessel have cargoes
        var orgVesselIndex: Int
        var destVesselIndex: Int
        do {
            orgVesselIndex = Main.rand.nextInt(sub.size)
            destVesselIndex = Main.rand.nextInt(sub.size)
        } while (orgVesselIndex == destVesselIndex || sub[orgVesselIndex].isEmpty())

        //pick a random cargo within the origin vessel
        val cargo = sub[orgVesselIndex].random(Main.rand)

        //add both at the end of the new vessel. Do this first as the new destination vessel might not be feasible

        val destOldSize = sub[destVesselIndex].size
        //the destination array needs to be two element larger for the new cargo to fit
        val destNew = sub[destVesselIndex].copyOf(destOldSize + 2)
        if (destOldSize > 0) {
            //insert the values randomly
            destNew.insert(Main.rand.nextInt(destOldSize), cargo)
            //second time around we need to account for the element we just inserted
            destNew.insert(Main.rand.nextInt(destOldSize + 1), cargo)
        } else {
            destNew[0] = cargo
            destNew[1] = cargo
        }
        val destFeasible = Operator.exchangeOnceTilFeasible(sol, destVesselIndex, destNew)
        if (!destFeasible) {
            log.trace { "Failed to reinsert $cargo from vessel $orgVesselIndex to $destVesselIndex as no feasible arrangement could be found" }
            return
        }
        sub[destVesselIndex] = destNew

        //remove cargo from the original vessel

        //create new array with two less elements as they will no longer be here
        val orgNew = sub[orgVesselIndex].filter(cargo, IntArray(sub[orgVesselIndex].size - 2))

        val orgFeasible = Operator.exchangeOnceTilFeasible(sol, orgVesselIndex, orgNew)
        if (!orgFeasible) {
            log.trace { "Failed to reinsert $cargo from vessel $orgVesselIndex to $orgVesselIndex as no feasible arrangement could be found" }
            return
        }
        sub[orgVesselIndex] = orgNew
        sol.joinToArray(sub)
    }
}
