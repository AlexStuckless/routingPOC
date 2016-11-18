package com.capstone16;

import java.time.LocalDateTime;
import java.util.*;
import com.google.ortools.constraintsolver.*;

class Pair<K, V> {
    final K first;
    final V second;

    public static <K, V> Pair<K, V> of(K element0, V element1) {
        return new Pair<K, V>(element0, element1);
    }

    public Pair(K element0, V element1) {
        this.first = element0;
        this.second = element1;
    }
}

class Delivery{
    public Location pickup;
    public Location dropoff;

    public Delivery(Location pu, Location dr){
        this.pickup = pu;
        this.dropoff = dr;
    }
}

class Location{
    public String address;
    public LocalDateTime time;

    public Location(String addr, LocalDateTime time){
        this.address = addr;
        this.time = time;
    }
}

public class Router {

    public void createRoute(){
        System.out.println("running app...");
        solve(4,2);
    }

    private List<Delivery> createDummyOrders(){
        Location p1 = new Location("2416 W 3rd ave, Vancouver, BC", LocalDateTime.now());
        Location d1 = new Location("1188 Richards St, Vancouver, BC", LocalDateTime.now().plusHours(1));
        Delivery x1 = new Delivery(p1,d1);

        return Arrays.asList(x1);
    }

    private void solve(final int numberOfOrders, final int numberOfVehicles) {
        System.out.println("Calling solve..");

        List<Pair<Integer, Integer>> locations = new ArrayList();
        locations.add(new Pair(0,0));
        locations.add(new Pair(0,2));
        locations.add(new Pair(2,2));
        locations.add(new Pair(1,1));

        List<Pair<Integer, Integer>> orderTimeWindows = new ArrayList();
        orderTimeWindows.add(new Pair(5,10));
        orderTimeWindows.add(new Pair(15,16));
        orderTimeWindows.add(new Pair(15,16));
        orderTimeWindows.add(new Pair(35,40));

        List<Integer> orderPenalties = new ArrayList();
        orderPenalties.add(1000);
        orderPenalties.add(1000);
        orderPenalties.add(1000);
        orderPenalties.add(1000);


        RoutingModel model =
                new RoutingModel(4, 2, 0);

        model.AddPickupAndDelivery(0,1);
        model.AddPickupAndDelivery(2,3);

        // Setting up dimensions
        final int bigNumber = 100000;
        NodeEvaluator2 manhattanCallback = new NodeEvaluator2(){
            @Override
            public long run(int firstIndex, int secondIndex) {
                try {
                    Pair<Integer, Integer> firstLocation = locations.get(firstIndex);
                    Pair<Integer, Integer> secondLocation = locations.get(secondIndex);
                    return Math.abs(firstLocation.first - secondLocation.first) +
                            Math.abs(firstLocation.second - secondLocation.second);
                } catch (Throwable throwed) {
                    System.out.println(throwed.getMessage());
                    return 0;
                }
            }
        };
        model.addDimension(manhattanCallback, bigNumber, bigNumber, false, "time");
        NodeEvaluator2 demandCallback = new NodeEvaluator2(){
            @Override
            public long run(int firstIndex, int secondIndex) {
                try {
                    return 1;
                } catch (Throwable throwed) {
                    System.out.println(throwed.getMessage());
                    return 0;
                }
            }
        };
        model.addDimension(demandCallback, 0, 20, true, "capacity");

        // Setting up vehicles
        for (int vehicle = 0; vehicle < numberOfVehicles; ++vehicle) {
            final int costCoefficient = 1;
            NodeEvaluator2 manhattanCostCallback = new NodeEvaluator2() {
                @Override
                public long run(int firstIndex, int secondIndex) {
                    try {
                        Pair<Integer, Integer> firstLocation = locations.get(firstIndex);
                        Pair<Integer, Integer> secondLocation = locations.get(secondIndex);
                        return costCoefficient *
                                (Math.abs(firstLocation.first - secondLocation.first) +
                                        Math.abs(firstLocation.second - secondLocation.second));
                    } catch (Throwable throwed) {
                        System.out.println(throwed.getMessage());
                        return 0;
                    }
                }
            };
            model.setVehicleCost(vehicle, manhattanCostCallback);
            model.cumulVar(model.end(vehicle), "time").setMax(1000);
        }

        // Setting up orders
        for (int order = 0; order < numberOfOrders; ++order) {
            model.cumulVar(order, "time").setRange(
                    orderTimeWindows.get(order).first,
                    orderTimeWindows.get(order).second);
            int[] orders = {order};
            model.addDisjunction(orders, orderPenalties.get(order));
        }

        // Solving
        RoutingSearchParameters parameters =
                RoutingSearchParameters.newBuilder()
                        .mergeFrom(RoutingModel.defaultSearchParameters())
                        .setFirstSolutionStrategy(FirstSolutionStrategy.Value.ALL_UNPERFORMED)
                        .build();

        System.out.println("Search");
        Assignment solution = model.solveWithParameters(parameters);

        if (solution != null) {
            String output = "Total cost: " + solution.objectiveValue() + "\n";
            // Dropped orders
            String dropped = "";
            for (int order = 0; order < numberOfOrders; ++order) {
                if (solution.value(model.nextVar(order)) == order) {
                    dropped += " " + order;
                }
            }
            if (dropped.length() > 0) {
                output += "Dropped orders:" + dropped + "\n";
            }
            // Routes
            for (int vehicle = 0; vehicle < numberOfVehicles; ++vehicle) {
                String route = "Vehicle " + vehicle + ": ";
                long order = model.start(vehicle);
                if (model.isEnd(solution.value(model.nextVar(order)))) {
                    route += "Empty";
                } else {
                    for (;
                         !model.isEnd(order);
                         order = solution.value(model.nextVar(order))) {
                        IntVar load = model.cumulVar(order, "capacity");
                        IntVar time = model.cumulVar(order, "time");
                        route += order + " Load(" + solution.value(load) + ") " +
                                "Time(" + solution.min(time) + ", " + solution.max(time) +
                                ") -> ";
                    }
                    IntVar load = model.cumulVar(order, "capacity");
                    IntVar time = model.cumulVar(order, "time");
                    route += order + " Load(" + solution.value(load) + ") " +
                            "Time(" + solution.min(time) + ", " + solution.max(time) + ")";
                }
                output += route + "\n";
            }
            System.out.println(output);
        }
    }
}
