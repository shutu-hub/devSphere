package com.shutu.model.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "游标分页返回结果")
public class CursorPage<T> {

    @Schema(description = "当前页的记录列表")
    private List<T> records;

    @Schema(description = "用于查询下一页（更早历史）的游标。如果 hasMore 为 false，则此值为 null。")
    private String nextCursor; 

    @Schema(description = "是否还有更早的历史记录")
    private Boolean hasMore;
}