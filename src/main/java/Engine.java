import Crawler.Crawler;
import Indexer.ImageIndexer;
import Indexer.TextIndexer;
import Pagerank.PageRank;


public class Engine {
    Engine() { }

    public static void main(String[] args) throws Exception {
        Crawler.main(args);
        TextIndexer indexer = new TextIndexer();
        ImageIndexer imageIndexer = new ImageIndexer();
        indexer.runIndexer();
        imageIndexer.runIndexer();
        PageRank.main(args);

    }
}
