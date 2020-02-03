/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.threads.output;

import com.edenrump.toolkit.graph.DepthDirection;
import com.edenrump.toolkit.graph.Graph;
import com.edenrump.toolkit.models.ThreadsData;
import com.edenrump.toolkit.models.VertexData;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.property.VerticalAlignment;
import javafx.scene.paint.Color;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PDFExporter {

    private static PdfFont body;
    private static PdfFont header;

    public static void exportCTDGraphToPDF(File file, ThreadsData threadsData) throws IOException {
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(file));
        Document doc = new Document(pdfDoc);

        body = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        header = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        //set the header of the section - should be done once for each depth 0 node
        Paragraph contentHeader = new Paragraph(threadsData.getName()).setFont(header);
        doc.add(contentHeader);
        doc.add(new Paragraph());

        threadsData.getVertices().stream()
                .filter(vertexData -> vertexData.getDepth() == 0)
                .sorted(Comparator.comparingInt(VertexData::getPriority))
                .forEach(vertexData -> addSectionContent(
                        Graph.unidirectionalFill(vertexData.getId(), DepthDirection.INCREASING_DEPTH, threadsData.getVertices()),
                        doc,
                        vertexData));
        doc.close();
    }

    private static void addSectionContent(List<VertexData> vertices, Document doc, VertexData root) {

        Paragraph sectionHeader = new Paragraph(root.getName()).setFont(header);
        doc.add(sectionHeader);

        //add a table from internal documents -> raw data documents -> raw data document link
        Table table = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();

        int internalDocDepth = 1;
        int rootDataDepth = 2;

        vertices.stream()
                .filter(vertexData -> vertexData.getDepth() == internalDocDepth)
                .sorted(Comparator.comparingInt(VertexData::getPriority))
                .forEach(internalDoc -> {
                    //get all leaves -> important to know rowSpan for internal document!
                    List<VertexData> leaves = new ArrayList<>();
                    for (VertexData vertexData : vertices) {
                        if (vertexData.getDepth() == rootDataDepth && internalDoc.getConnectedVertices().contains(vertexData.getId())) {
                            leaves.add(vertexData);
                        }
                    }

                    //TODO: add option to include link with internal doc cell
                    Cell internalDocCell = linkContentCell(internalDoc, "", leaves.size(), 1);

                    table.addCell(internalDocCell);
                    for (VertexData rawDataVertex : leaves) {
                        table.addCell(linkContentCell(rawDataVertex, "(no link)", 1, 1));
                    }
                });
        doc.add(table);
        doc.setTextAlignment(TextAlignment.CENTER);
        doc.add(new Paragraph(""));
        doc.add(new Paragraph(""));
        doc.add(new Paragraph(""));
        doc.setTextAlignment(TextAlignment.LEFT);
    }

    private static Cell linkContentCell(VertexData data, String noLinkText, int rowSpan, int colSpan) {
        Cell cell = new Cell(rowSpan, colSpan).add(new Paragraph(data.getName())).setFont(body);
        if (data.hasProperty("url")) {
            cell.add(getAnchorTag("", "Link", data.getProperty("url"), ""));
        } else if (noLinkText.length() > 0) {
            cell.add(new Paragraph(noLinkText));
        }

        if (data.hasProperty("color")) {
            Color hex = Color.web(data.getProperty("color"));
            cell.setBackgroundColor(new DeviceRgb((float) hex.getRed(), (float) hex.getGreen(), (float) hex.getBlue()));
            if (hex.getBrightness() < 0.6) {
                cell.setFontColor(ColorConstants.WHITE);
            }
        }

        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        cell.setPadding(5);

        return cell;
    }

    private static Paragraph getAnchorTag(String startText, String linkText, String url, String endText) {
        Paragraph paragraph = new Paragraph().setFontColor(ColorConstants.BLUE);
        paragraph.add(startText + " ");
        Link chunk = new Link(linkText,
                PdfAction.createURI(url));
        paragraph.add(chunk);
        paragraph.add(" " + endText);
        return paragraph;
    }
}
