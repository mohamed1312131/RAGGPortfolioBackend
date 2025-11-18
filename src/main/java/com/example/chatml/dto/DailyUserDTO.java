package com.example.chatml.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserDTO {
    private String date;
    private Integer users;
    private Integer questions;
}
