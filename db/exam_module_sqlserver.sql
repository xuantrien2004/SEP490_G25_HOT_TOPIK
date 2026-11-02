/* =====================================================================
   File này đã lỗi thời.
   Schema đề thi cũ (question_types.name, ranges, required_count, …)
   không còn khớp entity.

   Dùng db/hottopik_sqlserver.sql — schema đủ 27 bảng của project hiện tại,
   database HotTopik_2, collation Vietnamese_100_CI_AS.

   Script dưới đây dừng ngay để khỏi tạo nhầm bảng cũ.
   ===================================================================== */
THROW 51002, N'Dừng. db/exam_module_sqlserver.sql đã lỗi thời. Chạy db/hottopik_sqlserver.sql trên database trống HotTopik_2.', 1;
GO
