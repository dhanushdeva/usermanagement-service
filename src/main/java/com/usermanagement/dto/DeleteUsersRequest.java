package com.usermanagement.dto;

import java.util.List;

public record
DeleteUsersRequest(List<String> userIds) {
}
