package com.quantlab.signal.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
public class ExpiryDatesDTO {

   private LocalDateTime currentWeek;
   private LocalDateTime nextWeek;
   private LocalDateTime currentMonth;
   private LocalDateTime nextMonth;
   public ExpiryDatesDTO(LocalDateTime day){
       currentWeek = nextWeek = currentMonth = nextMonth = day;
   }

}
