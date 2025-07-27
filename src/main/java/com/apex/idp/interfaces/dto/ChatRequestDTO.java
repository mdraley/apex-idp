package com.apex.idp.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequestDTO {
    private String message;
    private String invoiceId;
}