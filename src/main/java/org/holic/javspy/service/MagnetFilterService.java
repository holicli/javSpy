package org.holic.javspy.service;
import org.holic.javspy.model.Magnet;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

@Service
public class MagnetFilterService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long MIN_BYTES = 2L * 1024 * 1024 * 1024; // 2GB
    private static final long MAX_BYTES = 5L * 1024 * 1024 * 1024; // 5GB

    /**
     * 获取时间最近且在2-5GB内的磁力链接，如果没有则返回时间最近的一部
     */
    public Magnet findOptimalMagnet(List<Magnet> magnets) {
        if (magnets == null || magnets.isEmpty()) {
            return null;
        }

        // 过滤无效日期
        List<Magnet> validMagnets = magnets.stream()
                .filter(this::isValidDate)
                .collect(Collectors.toList());

        if (validMagnets.isEmpty()) {
            return magnets.get(0); // 如果没有有效日期的，返回第一个
        }

        // 按日期排序（最新的在前）
        List<Magnet> sortedByDate = validMagnets.stream()
                .sorted(Comparator.comparing(this::parseDate).reversed())
                .collect(Collectors.toList());

        // 查找第一个大小在2-5GB内的
        for (Magnet magnet : sortedByDate) {
            if (magnet.getNumberSize() >= MIN_BYTES && magnet.getNumberSize() <= MAX_BYTES) {
                return magnet;
            }
        }

        // 没有找到大小合适的，返回最新的
        return sortedByDate.get(0);
    }

    /**
     * 获取所有符合大小条件的磁力链接，按时间倒序
     */
    public List<Magnet> findMagnetsInSizeRange(List<Magnet> magnets) {
        if (magnets == null || magnets.isEmpty()) {
            return new ArrayList<>();
        }

        return magnets.stream()
                .filter(m -> m.getNumberSize() >= MIN_BYTES && m.getNumberSize() <= MAX_BYTES)
                .filter(this::isValidDate)
                .sorted(Comparator.comparing(this::parseDate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 解析日期
     */
    private LocalDate parseDate(Magnet magnet) {
        try {
            return LocalDate.parse(magnet.getShareDate(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return LocalDate.MIN;
        }
    }

    /**
     * 检查日期是否有效
     */
    private boolean isValidDate(Magnet magnet) {
        try {
            LocalDate.parse(magnet.getShareDate(), DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * 工具方法：将字节数格式化为GB
     */
    public static double bytesToGB(long bytes) {
        return bytes / (1024.0 * 1024.0 * 1024.0);
    }
}
