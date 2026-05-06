package com.poly.graduation_project.controller;

import com.poly.graduation_project.model.Order;
import com.poly.graduation_project.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardExcelController {

    private final OrderRepository       orderRepository;
    private final UserRepository        userRepository;
    private final ProductRepository     productRepository;
    private final ReviewRepository      reviewRepository;

    @GetMapping("/admin/dashboard/export")
    public void exportExcel(
            @RequestParam(value = "period",   defaultValue = "month") String period,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate",   required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletResponse response) throws IOException {

        // ── Xác định khoảng thời gian ──── ─────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDt; 
        LocalDateTime endDt;
        String periodLabel;

        if (fromDate != null && toDate != null) {
            startDt     = fromDate.atStartOfDay();
            endDt       = toDate.atTime(23, 59, 59);
            periodLabel = "Từ " + fromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " đến " + toDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } else {
            startDt = switch (period) {
                case "day"  -> now.toLocalDate().atStartOfDay();
                case "week" -> now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay();
                case "year" -> now.toLocalDate().withDayOfYear(1).atStartOfDay();
                default     -> now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            };
            endDt = now;
            periodLabel = switch (period) {
                case "day"  -> "Hôm nay (" + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")";
                case "week" -> "Tuần này";
                case "year" -> "Năm " + now.getYear();
                default     -> "Tháng " + now.getMonthValue() + "/" + now.getYear();
            };
        }

        // ── Lấy đơn hàng trong kỳ ────────────────────────────────────────────
        final LocalDateTime fStart = startDt;
        final LocalDateTime fEnd   = endDt;

        List<Order> allOrders = orderRepository.findAllByOrderByCreateAtDesc();
        List<Order> periodOrders = allOrders.stream()
                .filter(o -> o.getCreateAt() != null
                        && !o.getCreateAt().isBefore(fStart)
                        && !o.getCreateAt().isAfter(fEnd))
                .collect(Collectors.toList());
 
        // ── KPI ───────────────────────────────────────────────────────────────
        BigDecimal revenue = periodOrders.stream()
            .filter(o -> o.getStatus() != null && o.getStatus() == 4)
            .map(o -> {
                BigDecimal total = o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO;
                BigDecimal ship = o.getShippingFee() != null ? o.getShippingFee() : BigDecimal.ZERO;
                return total.subtract(ship);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long orderCount = periodOrders.size();

        long newCustomers = userRepository.findByRole(false).stream()
                .filter(u -> orderRepository.findByUserOrderByCreateAtDesc(u).stream()
                        .min(Comparator.comparing(Order::getCreateAt))
                        .map(o -> !o.getCreateAt().isBefore(fStart) && !o.getCreateAt().isAfter(fEnd))
                        .orElse(false))
                .count();

        long soldQty = periodOrders.stream()
                .filter(o -> o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .mapToLong(od -> od.getQuantity() != null ? od.getQuantity() : 0)
                .sum();

        // Trạng thái đơn
        long cntCompleted = countByStatus(periodOrders, 4);
        long cntPending   = countByStatus(periodOrders, 0);
        long cntShipping  = countByStatus(periodOrders, 2);
        long cntCancelled = countByStatus(periodOrders, 5);

        // ── Tất cả sản phẩm bán được  ──────────────────
        Map<Integer, long[]> productStats = new LinkedHashMap<>();
        periodOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != 5 && o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .forEach(od -> {
                    if (od.getProduct() == null) return;
                    int  pid = od.getProduct().getId();
                    long qty = od.getQuantity() != null ? od.getQuantity() : 0;
                    long rev = od.getPrice()    != null
                            ? od.getPrice().multiply(BigDecimal.valueOf(qty)).longValue() : 0;
                    productStats.merge(pid, new long[]{qty, rev},
                            (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]});
                });

        // Sắp xếp theo số lượng bán giảm dần
        List<Map.Entry<Integer, long[]>> sortedProducts = productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .collect(Collectors.toList());

        // ── Top danh mục ──────────────────────────────────────────────────────
        Map<String, Long> catSales = new LinkedHashMap<>();
        periodOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != 5 && o.getOrderDetails() != null)
                .flatMap(o -> o.getOrderDetails().stream())
                .filter(od -> od.getProduct() != null && od.getProduct().getCategory() != null)
                .forEach(od -> {
                    String cat = od.getProduct().getCategory().getName();
                    long   qty = od.getQuantity() != null ? od.getQuantity() : 0;
                    catSales.merge(cat, qty, Long::sum);
                });

        // ── Tạo workbook ──────────────────────────────────────────────────────
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // ===== STYLES =====
            // Màu chủ đạo: xanh lá BeoBooks
            XSSFColor green     = new XSSFColor(new byte[]{(byte)25,  (byte)135, (byte)84},  null);
            XSSFColor lightGreen= new XSSFColor(new byte[]{(byte)212, (byte)237, (byte)218}, null);
            XSSFColor white     = new XSSFColor(new byte[]{(byte)255, (byte)255, (byte)255}, null);
            XSSFColor darkGray  = new XSSFColor(new byte[]{(byte)52,  (byte)58,  (byte)64},  null);
            XSSFColor lightGray = new XSSFColor(new byte[]{(byte)248, (byte)249, (byte)250}, null);
            XSSFColor gold      = new XSSFColor(new byte[]{(byte)255, (byte)193, (byte)7},   null);
            XSSFColor silver    = new XSSFColor(new byte[]{(byte)173, (byte)181, (byte)189}, null);
            XSSFColor bronze    = new XSSFColor(new byte[]{(byte)205, (byte)127, (byte)50},  null);
            XSSFColor redColor  = new XSSFColor(new byte[]{(byte)220, (byte)53,  (byte)69},  null);
            XSSFColor blueColor = new XSSFColor(new byte[]{(byte)13,  (byte)110, (byte)253}, null);

            Font fontTitle = wb.createFont();
            fontTitle.setFontName("Arial");
            fontTitle.setBold(true);
            fontTitle.setFontHeightInPoints((short) 18);
            fontTitle.setColor(IndexedColors.WHITE.getIndex());

            Font fontSubtitle = wb.createFont();
            fontSubtitle.setFontName("Arial");
            fontSubtitle.setFontHeightInPoints((short) 11);
            fontSubtitle.setColor(IndexedColors.WHITE.getIndex());

            Font fontHeader = wb.createFont();
            fontHeader.setFontName("Arial");
            fontHeader.setBold(true);
            fontHeader.setFontHeightInPoints((short) 10);
            fontHeader.setColor(IndexedColors.WHITE.getIndex());

            Font fontBold = wb.createFont();
            fontBold.setFontName("Arial");
            fontBold.setBold(true);
            fontBold.setFontHeightInPoints((short) 10);

            Font fontNormal = wb.createFont();
            fontNormal.setFontName("Arial");
            fontNormal.setFontHeightInPoints((short) 10);

            Font fontGreen = wb.createFont();
            fontGreen.setFontName("Arial");
            fontGreen.setBold(true);
            fontGreen.setFontHeightInPoints((short) 10);
            fontGreen.setColor(IndexedColors.GREEN.getIndex());

            // Style: tiêu đề sheet (header xanh đậm)
            XSSFCellStyle styleTitle = wb.createCellStyle();
            styleTitle.setFont(fontTitle);
            styleTitle.setFillForegroundColor(green);
            styleTitle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleTitle.setAlignment(HorizontalAlignment.CENTER);
            styleTitle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: subtitle (xanh nhạt hơn)
            XSSFCellStyle styleSubtitle = wb.createCellStyle();
            styleSubtitle.setFont(fontSubtitle);
            styleSubtitle.setFillForegroundColor(green);
            styleSubtitle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleSubtitle.setAlignment(HorizontalAlignment.CENTER);

            // Style: header cột (xanh đậm)
            XSSFCellStyle styleHeader = wb.createCellStyle();
            styleHeader.setFont(fontHeader);
            styleHeader.setFillForegroundColor(green);
            styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleHeader.setAlignment(HorizontalAlignment.CENTER);
            styleHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(styleHeader);

            // Style: ô dữ liệu thường
            XSSFCellStyle styleData = wb.createCellStyle();
            styleData.setFont(fontNormal);
            styleData.setFillForegroundColor(white);
            styleData.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleData);
            styleData.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: ô dữ liệu nền xám (zebra)
            XSSFCellStyle styleDataAlt = wb.createCellStyle();
            styleDataAlt.setFont(fontNormal);
            styleDataAlt.setFillForegroundColor(lightGray);
            styleDataAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleDataAlt);
            styleDataAlt.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: số tiền (màu xanh lá, bold)
            XSSFCellStyle styleMoney = wb.createCellStyle();
            styleMoney.setFont(fontGreen);
            DataFormat df = wb.createDataFormat();
            styleMoney.setDataFormat(df.getFormat("#,##0"));
            styleMoney.setFillForegroundColor(white);
            styleMoney.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleMoney);
            styleMoney.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: số tiền zebra
            XSSFCellStyle styleMoneyAlt = wb.createCellStyle();
            styleMoneyAlt.setFont(fontGreen);
            styleMoneyAlt.setDataFormat(df.getFormat("#,##0"));
            styleMoneyAlt.setFillForegroundColor(lightGray);
            styleMoneyAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleMoneyAlt);
            styleMoneyAlt.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: số có format ngàn
            XSSFCellStyle styleNumber = wb.createCellStyle();
            styleNumber.setFont(fontNormal);
            styleNumber.setDataFormat(df.getFormat("#,##0"));
            styleNumber.setFillForegroundColor(white);
            styleNumber.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleNumber);
            styleNumber.setAlignment(HorizontalAlignment.RIGHT);
            styleNumber.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle styleNumberAlt = wb.createCellStyle();
            styleNumberAlt.setFont(fontNormal);
            styleNumberAlt.setDataFormat(df.getFormat("#,##0"));
            styleNumberAlt.setFillForegroundColor(lightGray);
            styleNumberAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleNumberAlt);
            styleNumberAlt.setAlignment(HorizontalAlignment.RIGHT);
            styleNumberAlt.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: căn giữa
            XSSFCellStyle styleCenter = wb.createCellStyle();
            styleCenter.setFont(fontNormal);
            styleCenter.setFillForegroundColor(white);
            styleCenter.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleCenter);
            styleCenter.setAlignment(HorizontalAlignment.CENTER);
            styleCenter.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle styleCenterAlt = wb.createCellStyle();
            styleCenterAlt.setFont(fontNormal);
            styleCenterAlt.setFillForegroundColor(lightGray);
            styleCenterAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleCenterAlt);
            styleCenterAlt.setAlignment(HorizontalAlignment.CENTER);
            styleCenterAlt.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: KPI label
            XSSFCellStyle styleKpiLabel = wb.createCellStyle();
            styleKpiLabel.setFont(fontBold);
            styleKpiLabel.setFillForegroundColor(lightGreen);
            styleKpiLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleKpiLabel);
            styleKpiLabel.setVerticalAlignment(VerticalAlignment.CENTER);

            // Style: KPI value
            XSSFCellStyle styleKpiValue = wb.createCellStyle();
            styleKpiValue.setFont(fontBold);
            styleKpiValue.setDataFormat(df.getFormat("#,##0"));
            styleKpiValue.setFillForegroundColor(white);
            styleKpiValue.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(styleKpiValue);
            styleKpiValue.setVerticalAlignment(VerticalAlignment.CENTER);

            // ============================================================
            // SHEET 1: TỔNG QUAN
            // ============================================================
            XSSFSheet sheet1 = wb.createSheet("Tổng quan KPI");
            sheet1.setColumnWidth(0, 35 * 256);
            sheet1.setColumnWidth(1, 25 * 256);

            int r1 = 0;

            // Tiêu đề
            Row titleRow1 = sheet1.createRow(r1++);
            titleRow1.setHeightInPoints(40);
            Cell tc1 = titleRow1.createCell(0);
            tc1.setCellValue("BEOBOOKS - BÁO CÁO TỔNG QUAN");
            tc1.setCellStyle(styleTitle);
            sheet1.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            Row subRow1 = sheet1.createRow(r1++);
            subRow1.setHeightInPoints(22);
            Cell sc1 = subRow1.createCell(0);
            sc1.setCellValue("Kỳ báo cáo: " + periodLabel + "   |   Xuất lúc: "
                    + now.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
            sc1.setCellStyle(styleSubtitle);
            sheet1.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));

            r1++; // dòng trống

            // Header KPI
            Row hRow1 = sheet1.createRow(r1++);
            hRow1.setHeightInPoints(22);
            createCell(hRow1, 0, "CHỈ SỐ", styleHeader);
            createCell(hRow1, 1, "GIÁ TRỊ", styleHeader);

            // Dữ liệu KPI
            Object[][] kpis = {
                {"Doanh thu (VNĐ)",         revenue.longValue()},
                {"Số đơn hàng",             orderCount},
                {"Khách hàng mới",          newCustomers},
                {"Sản phẩm bán ra (cuốn)",  soldQty},
                {"Đơn hoàn thành",          cntCompleted},
                {"Đơn đang giao",           cntShipping},
                {"Đơn chờ xác nhận",        cntPending},
                {"Đơn đã hủy",              cntCancelled},
                {"Tổng đơn hệ thống",       orderRepository.count()},
                {"Tổng khách hàng",         (long) userRepository.findByRole(false).size()},
                {"Tổng sản phẩm",           productRepository.count()},
            };

            for (int i = 0; i < kpis.length; i++) {
                Row row = sheet1.createRow(r1++);
                row.setHeightInPoints(20);
                boolean isAlt = i % 2 == 1;
                createCell(row, 0, (String) kpis[i][0], isAlt ? styleDataAlt : styleKpiLabel);
                Cell valCell = row.createCell(1);
                valCell.setCellValue(((Number) kpis[i][1]).doubleValue());
                valCell.setCellStyle(i == 0 ? styleMoney : styleKpiValue);
            }

            // ============================================================
            // SHEET 2: TẤT CẢ SẢN PHẨM BÁN ĐƯỢC
            // ============================================================
            XSSFSheet sheet2 = wb.createSheet("Sản phẩm bán được");
            sheet2.setColumnWidth(0, 8  * 256);  // Xếp hạng
            sheet2.setColumnWidth(1, 38 * 256);  // Tên sách
            sheet2.setColumnWidth(2, 20 * 256);  // Danh mục
            sheet2.setColumnWidth(3, 10 * 256);  // Tác giả
            sheet2.setColumnWidth(4, 12 * 256);  // Đã bán
            sheet2.setColumnWidth(5, 20 * 256);  // Doanh thu
            sheet2.setColumnWidth(6, 10 * 256);  // Đánh giá
            sheet2.setColumnWidth(7, 12 * 256);  // Tồn kho

            int r2 = 0;

            // Tiêu đề
            Row titleRow2 = sheet2.createRow(r2++);
            titleRow2.setHeightInPoints(40);
            Cell tc2 = titleRow2.createCell(0);
            tc2.setCellValue("BEOBOOKS - DANH SÁCH SẢN PHẨM BÁN ĐƯỢC");
            tc2.setCellStyle(styleTitle);
            sheet2.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            Row subRow2 = sheet2.createRow(r2++);
            subRow2.setHeightInPoints(22);
            Cell sc2 = subRow2.createCell(0);
            sc2.setCellValue("Kỳ báo cáo: " + periodLabel + "   |   Tổng: " + sortedProducts.size() + " sản phẩm");
            sc2.setCellStyle(styleSubtitle);
            sheet2.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

            r2++; // dòng trống

            // Header
            Row hRow2 = sheet2.createRow(r2++);
            hRow2.setHeightInPoints(25);
            String[] headers2 = {"Xếp hạng", "Tên sách", "Danh mục", "Tác giả", "Đã bán (cuốn)", "Doanh thu (VNĐ)", "Đánh giá ★", "Tồn kho"};
            for (int i = 0; i < headers2.length; i++) {
                createCell(hRow2, i, headers2[i], styleHeader);
            }

            // Dữ liệu sản phẩm
            int rank = 1;
            for (Map.Entry<Integer, long[]> entry : sortedProducts) {
                com.poly.graduation_project.model.Product p =
                        productRepository.findById(entry.getKey()).orElse(null);
                if (p == null) { rank++; continue; }

                Row row = sheet2.createRow(r2++);
                row.setHeightInPoints(20);
                boolean isAlt = (rank % 2 == 0);

                // Xếp hạng
                String rankStr = rank == 1 ? "🥇 1" : rank == 2 ? "🥈 2" : rank == 3 ? "🥉 3" : String.valueOf(rank);
                createCell(row, 0, rankStr, isAlt ? styleCenterAlt : styleCenter);

                // Tên sách
                createCell(row, 1, p.getName() != null ? p.getName() : "", isAlt ? styleDataAlt : styleData);

                // Danh mục
                String catName = p.getCategory() != null ? p.getCategory().getName() : "";
                createCell(row, 2, catName, isAlt ? styleDataAlt : styleData);

                // Tác giả
                String authorName = p.getDisplayAuthor();
                createCell(row, 3, authorName, isAlt ? styleDataAlt : styleData);

                // Đã bán
                Cell soldCell = row.createCell(4);
                soldCell.setCellValue(entry.getValue()[0]);
                soldCell.setCellStyle(isAlt ? styleNumberAlt : styleNumber);

                // Doanh thu
                Cell revCell = row.createCell(5);
                revCell.setCellValue(entry.getValue()[1]);
                revCell.setCellStyle(isAlt ? styleMoneyAlt : styleMoney);

                // Đánh giá
                Double avg = reviewRepository.avgRatingByProductId(p.getId());
                double rating = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
                Cell ratingCell = row.createCell(6);
                ratingCell.setCellValue(rating);
                ratingCell.setCellStyle(isAlt ? styleCenterAlt : styleCenter);

                // Tồn kho
                Cell stockCell = row.createCell(7);
                stockCell.setCellValue(p.getStockQuantity() != null ? p.getStockQuantity() : 0);
                stockCell.setCellStyle(isAlt ? styleNumberAlt : styleNumber);

                rank++;
            }

            // Dòng tổng cộng
            if (!sortedProducts.isEmpty()) {
                Row totalRow = sheet2.createRow(r2++);
                totalRow.setHeightInPoints(22);
                createCell(totalRow, 0, "", styleHeader);
                createCell(totalRow, 1, "TỔNG CỘNG", styleHeader);
                createCell(totalRow, 2, "", styleHeader);
                createCell(totalRow, 3, "", styleHeader);
                Cell totalSoldCell = totalRow.createCell(4);
                totalSoldCell.setCellValue(soldQty);
                totalSoldCell.setCellStyle(styleHeader);
                Cell totalRevCell = totalRow.createCell(5);
                totalRevCell.setCellValue(revenue.longValue());
                totalRevCell.setCellStyle(styleHeader);
                createCell(totalRow, 6, "", styleHeader);
                createCell(totalRow, 7, "", styleHeader);
            }

            // ============================================================
            // SHEET 3: TOP DANH MỤC
            // ============================================================
            XSSFSheet sheet3 = wb.createSheet("Top danh mục");
            sheet3.setColumnWidth(0, 8  * 256);
            sheet3.setColumnWidth(1, 30 * 256);
            sheet3.setColumnWidth(2, 20 * 256);

            int r3 = 0;

            Row titleRow3 = sheet3.createRow(r3++);
            titleRow3.setHeightInPoints(40);
            Cell tc3 = titleRow3.createCell(0);
            tc3.setCellValue("BEOBOOKS - TOP DANH MỤC BÁN CHẠY");
            tc3.setCellStyle(styleTitle);
            sheet3.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

            Row subRow3 = sheet3.createRow(r3++);
            subRow3.setHeightInPoints(22);
            Cell sc3 = subRow3.createCell(0);
            sc3.setCellValue("Kỳ báo cáo: " + periodLabel);
            sc3.setCellStyle(styleSubtitle);
            sheet3.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

            r3++;

            Row hRow3 = sheet3.createRow(r3++);
            hRow3.setHeightInPoints(25);
            createCell(hRow3, 0, "Xếp hạng", styleHeader);
            createCell(hRow3, 1, "Danh mục", styleHeader);
            createCell(hRow3, 2, "Số lượng bán (cuốn)", styleHeader);

            List<Map.Entry<String, Long>> catList = catSales.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .collect(Collectors.toList());

            for (int i = 0; i < catList.size(); i++) {
                Row row = sheet3.createRow(r3++);
                row.setHeightInPoints(20);
                boolean isAlt = (i % 2 == 1);
                createCell(row, 0, String.valueOf(i + 1), isAlt ? styleCenterAlt : styleCenter);
                createCell(row, 1, catList.get(i).getKey(), isAlt ? styleDataAlt : styleData);
                Cell qtyCell = row.createCell(2);
                qtyCell.setCellValue(catList.get(i).getValue());
                qtyCell.setCellStyle(isAlt ? styleNumberAlt : styleNumber);
            }

            // ── Ghi response ──────────────────────────────────────────────────
            String fileName = "BeoBooks_BaoCao_"
                    + now.format(DateTimeFormatter.ofPattern("ddMMyyyy_HHmm")) + ".xlsx";

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, "UTF-8")
                            .replace("+", "%20"));
            wb.write(response.getOutputStream());
            response.flushBuffer();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setBorder(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private long countByStatus(List<Order> orders, int status) {
        return orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == status)
                .count();
    }
}