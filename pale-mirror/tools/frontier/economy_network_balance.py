#!/usr/bin/env python3
"""Read-only network allocation audit; never runs or mutates a simulation.

Input comes from FrontierV3EconomicCapacityDiagnostic's actual owner ports.
Uses scipy.optimize.milp (https://docs.scipy.org/doc/scipy/reference/generated/scipy.optimize.milp.html).
A static integer witness is NOT proof of route execution, bridge timing, storage
access or autonomous policy convergence. Infeasibility of conservative, current
capabilities is INCONCLUSIVE about future development, never world insolvency.
"""
import argparse
import json
import math
import sys


def assess(data, allow_initial_reserves=False):
    import numpy as np
    from scipy.optimize import Bounds, LinearConstraint, milp
    from scipy.sparse import csc_matrix

    if data.get("schema") != 1 or data["horizon"] <= 0:
        raise ValueError("unsupported owner-fact schema or horizon")
    nodes = {n["id"]: n for n in data["nodes"]}
    if len(nodes) != len(data["nodes"]) or not nodes or len(nodes) > 64:
        raise ValueError("duplicate or unbounded participant set")
    names, costs, upper, integral = [], [], [], []
    rows, lower_rows, upper_rows = [], [], []
    materials, money, work = {}, {}, {}

    def variable(name, limit, cost=0, integer=True):
        index = len(names)
        names.append(name); costs.append(cost); upper.append(limit); integral.append(int(integer))
        return index

    def add(row, index, value):
        row[index] = row.get(index, 0) + value

    def constraint(row, low=-math.inf, high=math.inf):
        rows.append(row); lower_rows.append(low); upper_rows.append(high)

    for node_id, node in sorted(nodes.items()):
        money[node_id] = {}
        for item in node["consumption"]:
            materials[node_id, item] = {}
        for process in node["processes"]:
            count = variable(("produce", node_id, process["id"]), process["maximumRecipes"], 0.001)
            operations = variable(("operations", node_id, process["id"]),
                                  math.ceil(process["maximumRecipes"] / process["recipesPerOperation"]))
            constraint({count: 1, operations: -process["recipesPerOperation"]}, high=0)
            add(materials[node_id, process["product"]], count, process["output"])
            for item, quantity in process["inputs"].items():
                add(materials[node_id, item], count, -quantity)
            heat = {count: -process["heatPerRecipe"]}
            for item, heat_units in process["fuels"].items():
                limit = math.ceil(process["maximumRecipes"] * process["heatPerRecipe"] / heat_units)
                burn = variable(("fuel", node_id, process["id"], item), limit, 0.001)
                add(heat, burn, heat_units); add(materials[node_id, item], burn, -1)
            constraint(heat, low=-process["retainedHeat"] if allow_initial_reserves else 0)
            if process["workers"]:
                labour = {operations: -process["labourPerOperation"]}
                for worker, capacity in process["workers"].items():
                    allocated = variable(("labour", node_id, process["id"], worker), capacity, integer=False)
                    add(labour, allocated, 1)
                    key = node_id, worker
                    if key in work and work[key][0] != capacity:
                        raise ValueError("owner ports disagree about a shared worker capacity")
                    work.setdefault(key, (capacity, {}))[1][allocated] = 1
                constraint(labour, low=0)

    # This is a conditional exchange opportunity, not an order or guaranteed buyer.
    # Finite conservation and every buyer's cash are simultaneous global constraints.
    total_material = sum(sum(n["consumption"].values()) + sum(n["stocks"].values())
                         + sum(p["maximumRecipes"] * p["output"] for p in n["processes"])
                         for n in nodes.values())
    for seller_id, seller in sorted(nodes.items()):
        for buyer_id, buyer in sorted(nodes.items()):
            if buyer_id == seller_id or buyer_id not in seller["known"] or seller_id not in buyer["known"]:
                continue
            buyer_quotes = {q["item"]: q for q in buyer["quotes"]}
            for quote in seller["quotes"]:
                item = quote["item"]
                if item not in buyer_quotes or buyer_quotes[item]["bid"] < quote["ask"]:
                    continue
                quantity = variable(("trade", seller_id, buyer_id, item, quote["ask"]), total_material, 1)
                add(materials[seller_id, item], quantity, -1)
                add(materials[buyer_id, item], quantity, 1)
                # Scale money to displayed units, retaining exact raw prices in the witness.
                add(money[seller_id], quantity, quote["ask"] / 1_000_000)
                add(money[buyer_id], quantity, -quote["ask"] / 1_000_000)
    for (node_id, item), row in materials.items():
        demand = nodes[node_id]["consumption"][item]
        initial = nodes[node_id]["stocks"].get(item, 0) if allow_initial_reserves else 0
        constraint(row, low=demand - initial)
    for node_id, row in money.items():
        constraint(row, low=-nodes[node_id]["cash"] / 1_000_000 if allow_initial_reserves else 0)
    for capacity, row in work.values():
        constraint(row, high=capacity)
    matrix = np.zeros((len(rows), len(names)))
    for row_index, row in enumerate(rows):
        for col, value in row.items():
            matrix[row_index, col] = value
    result = milp(np.array(costs), integrality=np.array(integral),
                  bounds=Bounds(np.zeros(len(names)), np.array(upper)),
                  constraints=LinearConstraint(csc_matrix(matrix), np.array(lower_rows), np.array(upper_rows)),
                  options={"time_limit": 30.0, "mip_rel_gap": 0.0})
    witness = []
    valid = result.x is not None
    if valid:
        candidate = result.x.copy()
        for index, is_integer in enumerate(integral):
            if is_integer:
                if abs(candidate[index] - round(candidate[index])) > 1e-6:
                    valid = False
                candidate[index] = round(candidate[index])
        actual = matrix @ candidate
        valid = valid and bool(np.all(actual >= np.array(lower_rows) - 1e-7)
                              and np.all(actual <= np.array(upper_rows) + 1e-7)
                              and np.all(candidate >= -1e-7) and np.all(candidate <= np.array(upper) + 1e-7))
        if valid:
            witness = [{"allocation": name, "quantity": float(candidate[i])}
                       for i, name in enumerate(names) if candidate[i] > 1e-7]
    return {"status": "STATIC_ALLOCATION_FOUND" if valid else "INCONCLUSIVE",
            "scope": "aggregate material, finite capacity, shared labour and settlement cash; not timed execution",
            "remainingChecks": ["initial stock and cash bridge timing", "actual logistics capacity and delivery",
                                "storage/service access", "autonomous choices", "future deposit development"],
            "usesInitialReserves": allow_initial_reserves, "tick": data["tick"], "horizon": data["horizon"],
            "ruleset": data["ruleset"], "solverStatus": int(result.status), "detail": result.message,
            "witness": witness}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("facts", help="owner-fact JSON file, or - for stdin")
    parser.add_argument("--allow-initial-reserves", action="store_true",
                        help="finite runway check, NOT evidence of a replenishing economy")
    args = parser.parse_args()
    if args.facts == "-":
        facts = json.load(sys.stdin)
    else:
        with open(args.facts, encoding="utf-8") as source:
            facts = json.load(source)
    print(json.dumps(assess(facts, args.allow_initial_reserves), ensure_ascii=False, indent=2))
