package com.draftflow;

import org.junit.jupiter.api.Test;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;

public class PrintCoverageTest {

    @Test
    public void printUncovered() throws Exception {
        File xmlFile = new File("build/reports/jacoco/test/jacocoTestReport.xml");
        if (!xmlFile.exists()) {
            System.out.println("JaCoCo XML report not found at " + xmlFile.getAbsolutePath());
            return;
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // Disable DTD loading to speed up parsing and avoid network requests
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList packages = doc.getElementsByTagName("package");
        System.out.println("==================================================");
        System.out.println("           UNCOVERED LINES AND BRANCHES           ");
        System.out.println("==================================================");

        for (int i = 0; i < packages.getLength(); i++) {
            Element pkg = (Element) packages.item(i);
            String pkgName = pkg.getAttribute("name");

            NodeList sourceFiles = pkg.getElementsByTagName("sourcefile");
            for (int j = 0; j < sourceFiles.getLength(); j++) {
                Element src = (Element) sourceFiles.item(j);
                String srcName = src.getAttribute("name");

                NodeList lines = src.getElementsByTagName("line");
                boolean printedHeader = false;

                for (int k = 0; k < lines.getLength(); k++) {
                    Element line = (Element) lines.item(k);
                    int mi = Integer.parseInt(line.getAttribute("mi")); // missed instructions
                    int mb = Integer.parseInt(line.getAttribute("mb")); // missed branches
                    int ci = Integer.parseInt(line.getAttribute("ci")); // covered instructions
                    int cb = Integer.parseInt(line.getAttribute("cb")); // covered branches

                    if (mi > 0 || mb > 0) {
                        if (!printedHeader) {
                            System.out.println("\nSource File: " + pkgName + "/" + srcName);
                            printedHeader = true;
                        }
                        System.out.printf("  Line %s: mi=%d, mb=%d, ci=%d, cb=%d\n",
                                line.getAttribute("nr"), mi, mb, ci, cb);
                    }
                }
            }
        }
        System.out.println("==================================================");
    }
}
