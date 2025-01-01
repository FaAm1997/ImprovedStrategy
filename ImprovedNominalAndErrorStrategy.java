public class ImprovedNominalAndErrorStrategy extends Strategy {

    private static final Logger logger = LogManager.getLogger(ImprovedNominalAndErrorStrategy.class);

    private final TestSequence globalNominalTestSequence = new TestSequence();
    private final TestSequence successfulSequences = new TestSequence();

    public void start() {

        OperationsSorter sorter = new GraphBasedOperationsSorter();
        TestRunner testRunner = TestRunner.getInstance();

        // Step 1: Nominal Testing with adaptive fuzzing
        while (!sorter.isEmpty()) {
            Operation operationToTest = sorter.getFirst();
            logger.debug("Testing operation " + operationToTest);

            NominalFuzzer nominalFuzzer = new NominalFuzzer(operationToTest);

            // Dynamic increase in test sequence count
            int nominalTestCount = calculateDynamicTestCount(operationToTest);
            List<TestSequence> nominalSequences = nominalFuzzer.generateTestSequences(nominalTestCount);

            for (TestSequence testSequence : nominalSequences) {
                testRunner.run(testSequence);

                // Evaluate using status code oracle
                StatusCodeOracle statusCodeOracle = new StatusCodeOracle();
                statusCodeOracle.assertTestSequence(testSequence);

                if (testSequence.isExecuted() && testSequence.get(0).getResponseStatusCode().isSuccessful()) {
                    successfulSequences.append(testSequence);
                }

                writeTestReports(testSequence);
            }

            globalNominalTestSequence.append(nominalSequences);
            sorter.removeFirst();
        }

        // Step 2: Adaptive filtering of nominal sequences
        globalNominalTestSequence.filterBySuccessfulStatusCode();

        // Step 3: Error Testing with data-driven fuzzing
        ErrorFuzzer errorFuzzer = new ErrorFuzzer(successfulSequences);
        int errorTestCount = 10 + (globalNominalTestSequence.size() / 2);
        List<TestSequence> errorSequences = errorFuzzer.generateTestSequences(errorTestCount);

        for (TestSequence errorSequence : errorSequences) {
            testRunner.run(errorSequence);

            // Additional evaluation for error responses
            StatusCodeOracle statusCodeOracle = new StatusCodeOracle();
            statusCodeOracle.assertTestSequence(errorSequence);

            writeTestReports(errorSequence);
        }

        // Step 4: Generate intermediate coverage reports
        generateCoverageReport();

        logger.info("Improved Nominal and Error Strategy execution complete.");
    }

    private int calculateDynamicTestCount(Operation operation) {
        // Adjust the number of tests dynamically based on the operation's complexity
        int parameterCount = operation.getAllRequestParameters().size();
        return 20 + (parameterCount * 2);
    }

    private void writeTestReports(TestSequence testSequence) {
        try {
            new ReportWriter(testSequence).write();
            new RestAssuredWriter(testSequence).write();
        } catch (IOException e) {
            logger.warn("Could not write test reports to file.");
            e.printStackTrace();
        }
    }

    private void generateCoverageReport() {
        try {
            new CoverageReportWriter(TestRunner.getInstance().getCoverage()).write();
        } catch (IOException e) {
            logger.warn("Could not write coverage report to file.");
            e.printStackTrace();
        }
    }
}
