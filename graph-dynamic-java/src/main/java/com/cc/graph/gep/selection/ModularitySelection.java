package com.cc.graph.gep.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.cc.graph.algorithm.Modularity;
import com.cc.graph.algorithm.params.ModularityParams;
import com.cc.graph.base.ImmutableGraph;
import com.cc.graph.gep.Chromosome;

public class ModularitySelection implements SelectionStrategy {

    @Override
    public SelectionResult choose(final List<Chromosome> chroms, final ImmutableGraph graph,
            final int chooseNum) {
        final int chromsSize = chroms.size();
        if (chromsSize == 0) {
            return new SelectionResult(Optional.empty(), chroms);
        }
        final List<Double> modularities = chroms.stream().map(c -> {
            final List<Set<String>> comms = c.toCommunityStyle();
            final double result = Modularity.instance.compute(ModularityParams.construct(comms, graph));
            return result < 0 ? 0 : result;
        }).collect(Collectors.toList());

        final List<Double> accModularities = new ArrayList<>(chromsSize);
        double acc = 0;
        int bestIndex = 0;
        double best = -1;
        for (int i = 0; i < modularities.size(); i++) {
            final double modularity = modularities.get(i);
            if (modularity > best) {
                best = modularity;
                bestIndex = i;
            }
            acc += modularity;
            accModularities.add(acc);
        }
        final double maxAccValue = accModularities.get(accModularities.size() - 1);
        final List<Chromosome> choosed = new ArrayList<>(chooseNum);
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < chooseNum; i++) {
            final double choosedValue = random.nextDouble(maxAccValue);
            int j = 0;
            for (; j < chromsSize; j++) {
                if (accModularities.get(j) > choosedValue) {
                    break;
                }
            }
            if (j < chromsSize) {
                choosed.add(chroms.get(j));
            } else {
                choosed.add(chroms.get(chromsSize - 1));
            }
        }
        return new SelectionResult(Optional.of(chroms.get(bestIndex)), choosed);
    }

}
