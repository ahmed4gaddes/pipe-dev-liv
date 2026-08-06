package com.pipedevliv.pipeline.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pipedevliv.pipeline.entity.PipelineExecution;
import com.pipedevliv.pipeline.entity.PipelineStage;
import com.pipedevliv.pipeline.repository.PipelineExecutionRepository;
import com.pipedevliv.pipeline.repository.PipelineStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.pipedevliv.common.exception.ResourceNotFoundException;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final PipelineStageRepository pipelineStageRepository;

    public byte[] generateExecutionReport(Long executionId) {
        PipelineExecution execution = pipelineExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("PipelineExecution", "id", executionId));
        
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             Document document = new Document()) {
            
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Rapport d'Exécution du Pipeline", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // General Info
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            
            document.add(new Paragraph("Informations Générales", boldFont));
            document.add(new Paragraph("Ticket ID : " + execution.getTicketId(), normalFont));
            document.add(new Paragraph("Environnement : " + execution.getEnvironment(), normalFont));
            document.add(new Paragraph("Statut : " + execution.getStatus(), normalFont));
            document.add(new Paragraph("Déclencheur : " + execution.getTriggerType(), normalFont));
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String startedAt = execution.getStartedAt() != null ? execution.getStartedAt().format(formatter) : "N/A";
            String completedAt = execution.getCompletedAt() != null ? execution.getCompletedAt().format(formatter) : "N/A";
            
            document.add(new Paragraph("Début : " + startedAt, normalFont));
            document.add(new Paragraph("Fin : " + completedAt, normalFont));
            document.add(new Paragraph("Branche : " + execution.getGitBranch(), normalFont));
            
            document.add(new Paragraph("\nDétails des Étapes", boldFont));
            document.add(new Paragraph("\n"));

            // Stages Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 3f, 2f, 2f});
            
            addTableHeader(table, boldFont);
            
            List<PipelineStage> stages = pipelineStageRepository.findByExecutionIdOrderByStageOrderAsc(execution.getId());
            for (PipelineStage stage : stages) {
                table.addCell(new Phrase(String.valueOf(stage.getStageOrder()), normalFont));
                table.addCell(new Phrase(stage.getName(), normalFont));
                table.addCell(new Phrase(stage.getStatus().name(), normalFont));
                String duration = stage.getDurationSeconds() != null ? stage.getDurationSeconds() + "s" : "-";
                table.addCell(new Phrase(duration, normalFont));
            }
            
            document.add(table);
            document.close(); // Close explicitly before calling toByteArray()
            return out.toByteArray();
            
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du rapport PDF", e);
        }
    }

    private void addTableHeader(PdfPTable table, Font font) {
        String[] headers = {"Ordre", "Nom de l'étape", "Statut", "Durée"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }
}
