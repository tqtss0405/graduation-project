# Giải thích chức năng hệ thống

Tài liệu này tổng hợp 8 chức năng chính của hệ thống, gồm luồng xử lý Backend và JavaScript (Frontend).

## 1) Quản lý đơn hàng (Admin Order)

### Mục tiêu
- Cho phép quản trị viên theo dõi, lọc, xem chi tiết và cập nhật trạng thái đơn hàng.

### Backend
- Endpoint GET `/admin/orders`:
  - Lọc theo `status`, `keyword` (tên/email), `fromDate`, `toDate`.
  - Sắp xếp ưu tiên trạng thái ở tab "Tất cả".
  - Phân trang theo `page`.
- Endpoint GET `/admin/orders/items/{id}`:
  - Trả JSON danh sách sản phẩm trong đơn (AJAX cho modal chi tiết).
- Endpoint POST `/admin/orders/update-status/{id}`:
  - Cập nhật trạng thái đơn theo rule nghiệp vụ.
  - Chặn các trường hợp không hợp lệ (đã hủy, đã hoàn tiền, đã hoàn thành...).

### JavaScript
- Trên trang admin orders:
  - Nhấn nút "Xem" sẽ đọc `data-*` và mở modal chi tiết.
  - Gọi `fetch('/admin/orders/items/{id}')` để đổ danh sách sản phẩm vào bảng trong modal.
  - JS render phân trang footer, giữ nguyên filter khi chuyển trang.

## 2) Thanh toán (Checkout)

### Mục tiêu
- Hỗ trợ đặt hàng COD và VNPay, tính phí ship động, áp dụng voucher.

### Backend
- GET `/user/checkout`:
  - Lấy giỏ hàng của user, lọc item hợp lệ (còn hàng, active).
  - Tính `subtotal`, nạp địa chỉ giao hàng.
- POST `/user/checkout/apply-voucher`:
  - Kiểm tra mã giảm giá (tồn tại, active, còn hạn, còn lượt dùng).
  - Tính giảm giá trên tổng đơn (`subtotal + shippingFee`).
- POST `/user/checkout/cod`:
  - Tạo order COD, lưu chi tiết đơn, trừ tồn kho, xóa cart item đã đặt.
- POST `/user/checkout/vnpay` + GET `/vnpay/return`:
  - Khởi tạo VNPay URL, verify callback, thành công thì lưu đơn.
- POST `/api/shipping-fee`:
  - Gọi GHN để lấy phí ship, có fallback khi lỗi.

### JavaScript
- Trên checkout:
  - `fetchShippingFee(...)`: tính phí ship theo district/ward.
  - Chọn địa chỉ có sẵn hoặc nhập địa chỉ mới (cascade tỉnh/quận/phường).
  - `applyVoucher()`: gọi endpoint apply voucher, cập nhật tổng tiền realtime.
  - `placeOrder()`: submit form COD hoặc VNPay với dữ liệu hidden input.

## 3) Quản lý giảm giá (Voucher)

### Mục tiêu
- Admin tạo/sửa/xóa voucher, theo dõi hiệu lực và lượt sử dụng.

### Backend
- GET `/admin/vouchers`: hiện danh sách voucher + thống kê.
- POST `/admin/vouchers/save`: tạo voucher mới.
- GET `/admin/vouchers/edit/{id}`: trả JSON voucher cho modal sửa.
- POST `/admin/vouchers/update/{id}`: cập nhật voucher.
- POST `/admin/vouchers/delete/{id}`: xóa voucher.
- `VoucherService`:
  - Validate dữ liệu, map DTO -> Entity.
  - Scheduler tự động disable voucher hết hạn theo chu kỳ.

### JavaScript
- Trang admin vouchers:
  - Tìm kiếm client-side theo tên/mã voucher.
  - Phân trang client-side.
  - `openEditModal(id)` fetch JSON để đổ dữ liệu vào modal.
  - Copy code, tạo mã tự động, confirm xóa, toast thông báo.

## 4) Tìm kiếm sản phẩm

### Mục tiêu
- Tìm sách theo từ khóa, danh mục, giá, sắp xếp, phân trang.

### Backend
- GET `/products`:
  - Nhận params: `keyword`, `categoryId`, `priceRange` hoặc `minPrice/maxPrice`, `sort`, `page`, `catPage`.
  - Chuẩn hóa tìm kiếm không dấu (`removeAccent`) và tìm theo nhiều từ.
  - Lọc danh mục, lọc giá, sắp xếp, phân trang.

### JavaScript
- `doSearch()`: build URL tìm kiếm và điều hướng.
- `navSearch()` ở navbar: tìm nhanh từ mọi trang.
- `selectPreset()` và `applyCustomPrice()`: áp dụng bộ lọc giá.

## 5) Xem chi tiết đơn hàng

### Mục tiêu
- Hiện đầy đủ thông tin đơn: khách hàng, thanh toán, trạng thái, danh sách sản phẩm.

### Backend
- Admin: GET `/admin/orders/items/{id}` trả line-items của đơn.
- User: GET `/user/order-details` trả danh sách đơn của user và thông tin phụ trợ đánh giá.

### JavaScript
- Admin:
  - Mở modal chi tiết, render thông tin tổng quan + danh sách sản phẩm.
  - Hiện thông tin tài khoản ngân hàng hoàn tiền nếu có.
- User:
  - Hiện chi tiết ngay trên page lịch sử đơn hàng.

## 6) Hủy đơn

### Mục tiêu
- Cho phép hủy đơn đúng điều kiện và đảm bảo nhập lại tồn kho.

### Backend
- POST `/user/order-details/cancel/{id}`:
  - Chỉ cho phép khi status = 0 hoặc 1.
  - Restock từng sản phẩm trong order detail.
  - Cập nhật status đơn = 5 (Đã hủy).

### JavaScript
- Modal xác nhận hủy đơn trên trang lịch sử mua hàng.
- Sau xác nhận, submit form hủy đơn.

## 7) Xem lịch sử mua hàng

### Mục tiêu
- Cho user theo dõi đơn đã đặt và thao tác tiếp theo.

### Backend
- GET `/user/order-details`:
  - Lấy đơn theo user, sắp xếp mới nhất.
  - Tạo `reviewedMap` và `hasUnreviewedMap` để điều khiển nút đánh giá.

### JavaScript
- Render card đơn hàng theo từng trạng thái.
- Toast thông báo từ flash message.
- Hỗ trợ mở review modal theo đơn.

## 8) Xác nhận đã nhận hàng

### Mục tiêu
- User xác nhận đã nhận được hàng để kết thúc đơn.

### Backend
- POST `/user/order-details/confirm-received/{id}`:
  - Kiểm tra user sở hữu đơn.
  - Chỉ hợp lệ khi status = 3.
  - Chuyển status sang 4 (Hoàn thành).
- `OrderScheduler`:
  - Tự động chuyển đơn đã giao quá 7 ngày sang hoàn thành nếu user chưa xác nhận.

### JavaScript
- Nút "Đã nhận hàng" mở modal xác nhận.
- Xác nhận xong sẽ submit form sang endpoint backend.

---

## Tệp tham chiếu chính
- `src/main/java/com/poly/graduation_project/controller/OrderController.java`
- `src/main/java/com/poly/graduation_project/controller/CheckoutController.java`
- `src/main/java/com/poly/graduation_project/controller/UserOrderController.java`
- `src/main/java/com/poly/graduation_project/controller/VoucherController.java`
- `src/main/java/com/poly/graduation_project/controller/IndexController.java`
- `src/main/java/com/poly/graduation_project/controller/ShippingController.java`
- `src/main/java/com/poly/graduation_project/service/VoucherService.java`
- `src/main/java/com/poly/graduation_project/service/OrderScheduler.java`
- `src/main/resources/templates/admin-orders.html`
- `src/main/resources/templates/checkout.html`
- `src/main/resources/templates/admin-vouchers.html`
- `src/main/resources/templates/products.html`
- `src/main/resources/templates/order-details.html`
- `src/main/resources/static/js/sidebar.js`
