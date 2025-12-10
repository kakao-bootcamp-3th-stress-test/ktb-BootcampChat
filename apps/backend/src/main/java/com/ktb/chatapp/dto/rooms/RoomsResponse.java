package com.ktb.chatapp.dto.rooms;

import com.ktb.chatapp.dto.PageMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomsResponse {
    private boolean success = true;
    private List<RoomResponse> data;
    private PageMetadata metadata;
}
