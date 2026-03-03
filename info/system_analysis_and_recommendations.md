# Báo cáo Phân tích Hệ thống & Đề xuất Cải tiến

## 1. Tóm tắt Yêu cầu Hệ thống Hiện tại
Dựa vào yêu cầu sếp đưa ra, hệ thống quản lý năng suất (Shoe Production EFF) được xây dựng trên nền tảng:
- **Ngôn ngữ & Framework**: Spring Boot MVC, Java 17.
- **Database**: SQL Server (SSMS).
- **Giao diện**: HTML/CSS/JS thuần, sử dụng Thymeleaf fragment để tái sử dụng component. CSS và JS được cấu trúc ra folder riêng biệt.
- **Bảo mật**: Spring Security để quản lý 3 phân quyền chính:
  - `User`: Khi đăng ký mặc định sẽ là role này. Nhiệm vụ chính là nhập liệu sản xuất hàng ngày (Ngày, Bộ phận, Chuyền, Mã giày, Output, Số công nhân, Giờ làm việc...).
  - `Manager`: Xem bảng thống kê/Dashboard tính toán EFF (Dựa vào việc lấy dữ liệu User nhập đối chiếu với dữ liệu master trong DB sheet). Có đặc quyền chỉnh sửa dữ liệu do User nhập vào.
  - `Admin`: Có toàn quyền hệ thống, quản lý tài khoản (cấp quyền lên Manager/Admin hoặc giáng cấp xuống User).

---

## 2. Phân tích Các Tính năng Nghiệp vụ Còn Thiếu (Recommendations)
Hệ thống hiện tại giải quyết tốt luồng nhập liệu và báo cáo cơ bản. Tuy nhiên, để đưa vào vận hành thực tế tại nhà máy sản xuất giày, sếp có thể cân nhắc bổ sung thêm các tính năng nghiệp vụ (Business Features) dưới đây:

### 2.1. Quản lý Dữ liệu Gốc (Master Data Management - MDM)
* **Vấn đề**: File Excel có sheet DB chứa thông tin định mức (Quota, Target Cycle Time - TCT, Allowance) cho từng Article No. / Shoe Name. Khi xưởng có mẫu giày mới, lấy dữ liệu đâu để đối chiếu tính EFF?
* **Đề xuất**: Cần có một trang cho phép `Manager` hoặc `Admin` quản lý danh mục dữ liệu gốc. Các chức năng bao gồm: Thêm mới, chỉnh sửa Quota, import danh sách mẫu giày từ file Excel vào Database SSMS.

### 2.2. Kiểm duyệt Dữ liệu (Approval Workflow)
* **Vấn đề**: `User` nhập liệu xong sẽ lập tức hiện lên Dashboard tính EFF. Nếu User nhập nhầm Output rất lớn, Dashboard sẽ bị sai lệch trước khi `Manager` kịp chỉnh sửa.
* **Đề xuất**: Thêm trạng thái (Status) cho phiếu nhập liệu (VD: `PENDING`, `APPROVED`, `REJECTED`). `Manager` xem Dashboard tạm tính, sau đó "Duyệt" phiếu thì số liệu bài báo cáo chính thức mới được cập nhật.

### 2.3. Lịch sử Chỉnh sửa (Audit Trail / Revision History)
* **Vấn đề**: Hệ thống cho phép `Manager` sửa dữ liệu của `User` (ví dụ: sửa sản lượng Output). Nếu có tranh chấp về lương/thưởng, người nhập liệu không biết ai là người đã sửa số của mình.
* **Đề xuất**: Lưu lại lịch sử các lần chỉnh sửa (Ai sửa, đổi từ giá trị cũ sang giá trị mới là bao nhiêu, và thời gian sửa). 

### 2.4. Theo dõi Hàng lỗi (Defect & RFT - Right First Time)
* **Vấn đề**: Việc tính hiệu suất (Efficiency) hiện chỉ dựa vào Output (Tổng sản lượng đầu ra).
* **Đề xuất**: Cần bổ sung trường nhập liệu số lượng Hàng lỗi (Defect). Năng suất thực tế nên được hỗ trợ thêm chỉ số RFT (Right First Time) để phản ánh chất lượng chuyền may, không chỉ chạy theo số lượng.

### 2.5. Theo dõi Thời gian Chết (Downtime Tracking)
* **Vấn đề**: Nếu chuyền bị dừng do cúp điện, cúp nước, hay hư rập/chờ vật tư, `User` nhập Working Hours bị thiếu hụt khiến EFF rớt thê thảm dù công nhân làm việc rất nhanh.
* **Đề xuất**: Bổ sung module nhập "Downtime" (thời gian chết và lý do). Công thức tính EFF sẽ trừ đi Downtime hợp lệ để bảo vệ quyền lợi tính lương/thưởng của chuyền.

### 2.6. Chức năng Xuất / Nhập Báo Cáo Excel (Export / Import)
* **Vấn đề**: Kế toán hoặc Ban giám đốc có thể cần báo cáo EFF định dạng Excel như trước đây.
* **Đề xuất**: Thêm tính năng Export các bảng Dashboard (thống kê) ra lại file `.xlsx` format chuẩn.

---
**Kết luận**: Sếp vui lòng xem xét các đề xuất trên. Nếu sếp muốn triển khai ngay phiên bản cốt lõi (Core MVP) theo đúng yêu cầu ban đầu (chưa cần các tính năng trên), tôi sẽ tự động tiến hành thiết lập kiến trúc, cấu trúc code Spring Boot và code các tính năng ngay khi sếp duyệt kế hoạch.
