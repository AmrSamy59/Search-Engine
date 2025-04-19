package Utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class Document {
    int docId;
    public String url, title, html, content;

    public Document(int docId, String url, String title, String content) {
        this.docId = docId;
        this.url = url;
        this.title = title;
        this.content = content;
        //System.out.println(this.content);
    }
    public int getId() {
        return docId;
    }

    public List<String> extractImageUrls() {
        List<String> imageUrls = new ArrayList<>();
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(content);
        Elements imgTags = jsoupDoc.select("img[src]");
        for (Element img : imgTags) {
            String src = img.absUrl("src");
            if (src.endsWith(".jpg") || src.endsWith(".png")) {
                imageUrls.add(src);
            }
        }
        return imageUrls;
    }
}
