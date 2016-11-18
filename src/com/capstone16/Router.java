package com.capstone16;

import java.time.LocalDateTime;
import java.util.*;
import com.google.ortools.constraintsolver.*;

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
    }

    private List<Delivery> createDummyOrders(){
        Location p1 = new Location("2416 W 3rd ave, Vancouver, BC", LocalDateTime.now());
        Location d1 = new Location("1188 Richards St, Vancouver, BC", LocalDateTime.now().plusHours(1));
        Delivery x1 = new Delivery(p1,d1);

        return Arrays.asList(x1);
    }

    private void solve(final int numberOfOrders, final int numberOfVehicles) {
        System.out.println("Calling solve..");
        List<Delivery> deliveries = createDummyOrders();
        final int numberOfLocations = deliveries.size() * 2;

        RoutingModel model =
                new RoutingModel(numberOfLocations, numberOfVehicles,
                        vehicleStarts, vehicleEnds);

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
                    logger.warning(throwed.getMessage());
                    return 0;
                }
            }
        };
        model.addDimension(manhattanCallback, bigNumber, bigNumber, false, "time");
        NodeEvaluator2 demandCallback = new NodeEvaluator2(){
            @Override
            public long run(int firstIndex, int secondIndex) {
                try {
                    if (firstIndex < numberOfOrders) {
                        return orderDemands.get(firstIndex);
                    }
                    return 0;
                } catch (Throwable throwed) {
                    logger.warning(throwed.getMessage());
                    return 0;
                }
            }
        };
        model.addDimension(demandCallback, 0, vehicleCapacity, true, "capacity");

        // Setting up vehicles
        for (int vehicle = 0; vehicle < numberOfVehicles; ++vehicle) {
            final int costCoefficient = vehicleCostCoefficients.get(vehicle);
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
                        logger.warning(throwed.getMessage());
                        return 0;
                    }
                }
            };
            model.setVehicleCost(vehicle, manhattanCostCallback);
            model.cumulVar(model.end(vehicle), "time").setMax(vehicleEndTime.get(vehicle));
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

        logger.info("Search");
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
            logger.info(output);
        }
    }
}
