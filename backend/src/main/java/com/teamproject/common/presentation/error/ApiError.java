package com.teamproject.common.presentation.error;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fieldErrors) {}
