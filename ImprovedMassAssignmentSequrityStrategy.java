public class ImprovedMassAssignmentSecurityStrategy extends Strategy {

    private static final Logger logger = LogManager.getLogger(ImprovedMassAssignmentSecurityStrategy.class);
    private final Environment environment = Environment.getInstance();

    @Override
    public void start() {

        // step1: extract crud information
        CrudInformationExtractor crudInformationExtractor = new CrudInformationExtractor();
        crudInformationExtractor.extract();
        CrudInferredVsGroundTruthComparator.compare();

        // Manage CRUD
        CrudManager crudManager = new CrudManager(environment.getOpenAPI());

        // step2: proccessing group CRUD
        for (CrudGroup crudGroup : crudManager.getInferredGroups()) {
            logger.info("Processing CRUD Group: " + crudGroup);

            // Generate test Mass Assignment
            MassAssignmentFuzzer massAssignmentFuzzer = new MassAssignmentFuzzer(crudGroup);
            massAssignmentFuzzer.setUseInferredCRUDInformation(true);
            massAssignmentFuzzer.setEnableRandomPayloads(true);
            List<TestSequence> testSequences = massAssignmentFuzzer.generateTestSequences();

            MassAssignmentOracle massAssignmentOracle = new MassAssignmentOracle();
            massAssignmentOracle.setUseInferredCRUDInformation(true);

            for (TestSequence testSequence : testSequences) {

                // run test and check answer
                TestRunner testRunner = TestRunner.getInstance();
                testRunner.run(testSequence);
                massAssignmentOracle.assertTestSequence(testSequence);

                //
                if (!testSequence.isFullyCovered()) {
                    logger.warn("Uncovered paths detected! Retrying with additional fuzzing...");
                    massAssignmentFuzzer.refineFuzzing(testSequence);
                }

                //
                writeTestReports(testSequence);
            }

            writeGlobalDebugReport();
        }

        generateCoverageReport();
    }

    private void writeTestReports(TestSequence testSequence) {
        try {
            new ReportWriter(testSequence).write();
        } catch (IOException e) {
            logger.warn("Could not write test report to file.");
        }
    }

    private void writeGlobalDebugReport() {
        try {
            TestSequence globalDebugSequence = TestRunner.globalTestSequenceForDebug;
            globalDebugSequence.setName("GlobalSequenceForDebugPurposes");
            new ReportWriter(globalDebugSequence).write();
        } catch (IOException e) {
            logger.warn("Could not write global debug report.");
        }
    }

    private void generateCoverageReport() {
        try {
            new CoverageReportWriter(TestRunner.getInstance().getCoverage()).write();
        } catch (IOException e) {
            logger.warn("Could not write coverage report.");
        }
    }
}
