package Ranker;

import Utils.WebDocument;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface Ranker {

     List<WebDocument> rank(List<String> queryTexts, List<String> tokensFirst, List<String> tokensSecond, Set<String> candidateDocsIds, String logicalOperator);
}
