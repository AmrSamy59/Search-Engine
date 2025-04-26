package Pagerank;

public class Main {
    public static void main(String[] args) {
        LinkGraphBuilder builder = new LinkGraphBuilder();
        builder.buildUrlIdMaps();
        builder.buildLinkGraphInMemory();
        builder.shutdownExecutor();

        PageRankCalculator calculator = new PageRankCalculator(
                builder.getIncomingLinks(),
                builder.getOutDegreeCache()
        );

        calculator.initializePageRanks();
        calculator.calculatePageRanks();
        calculator.savePageRanks();
    }
}
