public class ImprovedNLPStrategy extends Strategy {

    @Override
    public void start() {
        Environment env = Environment.getInstance();
        List<Operation> operations = env.getOpenAPI().getOperations();
        OperationsSorter sorter = new GraphBasedOperationsSorter();

        int totalOperationsCount = operations.size();
        int validatedOperationsCount = 0;

        System.out.println("NLP processing complete. Starting API validation...");

        testRunner.addResponseProcessor(nlpResponseProcessor);

        while (!sorter.isEmpty()) {
            Operation operation = sorter.getFirst();
            System.out.printf("Validating operation: %s with %d rules\n", operation.getOperationId(), operation.getRulesToValidate().size());

            Set<Rule> allRules = operation.getRulesToValidate();
            TestSequence successfulSequence = validateOperation(operation, allRules);

            if (successfulSequence != null) {
                validatedOperationsCount++;
                System.out.printf("Validated operation %s (%d/%d)\n", operation.getOperationId(), validatedOperationsCount, totalOperationsCount);
            } else {
                System.out.printf("Failed to validate operation %s\n", operation.getOperationId());
            }
            sorter.removeFirst();
        }

        System.out.println("Validation process completed.");
    }

    // Improve combine validation proccess
    private TestSequence validateOperation(Operation operation, Set<Rule> allRules) {
        Set<Rule> presenceRules = filterRules(allRules, Rule::isPresenceRule);
        Set<Rule> ipdRules = filterRules(allRules, Rule::isSetIpdRule);
        Set<Rule> constraintRules = filterRules(allRules, Rule::isConstraintRule);

        for (Set<Rule> presenceCombination : Sets.powerSet(presenceRules)) {
            if (!isCombinationOfPresenceRulesValid(presenceCombination)) continue;

            for (Set<Rule> ipdCombination : Sets.powerSet(ipdRules)) {
                if (!isCombinationOfSetIpdRulesValid(ipdCombination)) continue;

                for (Set<Rule> constraintCombination : Sets.powerSet(constraintRules)) {
                    if (!isCombinationOfConstraintRulesValid(constraintCombination)) continue;

                    Operation modifiedOperation = operation.deepClone();
                    applyRulesToOperation(modifiedOperation, presenceCombination, ipdCombination, constraintCombination);

                    TestSequence sequence = runDynamicValidation(modifiedOperation);
                    if (sequence != null && sequence.isExecuted()) {
                        return sequence;
                    }
                }
            }
        }
        return null;
    }

    // extract rule set by filter
    private Set<Rule> filterRules(Set<Rule> rules, Predicate<Rule> predicate) {
        return rules.stream().filter(predicate).collect(Collectors.toSet());
    }

    // implement rule on operation
    private void applyRulesToOperation(Operation operation, Set<Rule>... rulesSets) {
        for (Set<Rule> rules : rulesSets) {
            rules.forEach(rule -> rule.apply(operation));
        }
    }

}
