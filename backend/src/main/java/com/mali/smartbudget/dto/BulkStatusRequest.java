package com.mali.smartbudget.dto;

import java.util.List;

public record BulkStatusRequest(List<Long> userIds, boolean active) {}
