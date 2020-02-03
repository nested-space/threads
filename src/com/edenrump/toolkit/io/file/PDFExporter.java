/*
 * ******************************************************************************
 *  * Copyright (c) 05/12/2019, 09:12.2019. Edward Eden-Rump is licensed under a Creative Commons Attribution 4.0 International License.
 *  *
 *  * Based on a work at https://github.com/nested-space/
 *  To view a copy of this license, visit http://creativecommons.org/licenses/by/4.0/.
 *  *****************************************************************************
 */

package com.edenrump.toolkit.io.file;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
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
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.IOException;

public class PDFExporter {

    public static void exportToPDF(File file) throws IOException {
        PdfDocument pdfDoc;

        pdfDoc = new PdfDocument(new PdfWriter(file));
        Document doc = new Document(pdfDoc);

        PdfFont body = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont header = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        //set the header of the section - should be done once for each depth 0 node
        Paragraph contentHeader = new Paragraph("Section Content").setFont(header);
        doc.add(contentHeader);

        //add a table from internal documents -> raw data documents -> raw data document link
        Table table = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0) {
                Cell cell = new Cell()
                        .add(new Paragraph("Example text " + i))
                        .setFont(body);
                cell.setBackgroundColor(ColorConstants.RED);
                table.addCell(cell);
            }

            if (i % 2 == 1) {
                Paragraph paragraph = new Paragraph();
                paragraph.add("Example text with ");
                Link chunk = new Link("a link",
                        PdfAction.createURI("www.google.com"));
                paragraph.add(chunk);
                paragraph.add(" in the middle");
                table.addCell(paragraph);
            }
        }
        doc.add(table);
        doc.close();
    }
}
