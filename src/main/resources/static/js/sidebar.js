document.addEventListener("DOMContentLoaded", function () {
    // Lấy đường dẫn hiện tại
    const currentPath = window.location.pathname;

    // Lấy tất cả các link trong sidebar
    const navLinks = document.querySelectorAll(".sidebar .nav-link");

    // Xóa class 'active' khỏi tất cả các link
    navLinks.forEach((link) => {
        link.classList.remove("active");
    });

    // Tìm link khớp với URL hiện tại và thêm class 'active'
    navLinks.forEach((link) => {
        const linkPath = link.getAttribute("href");

        // So sánh chính xác
        if (linkPath === currentPath) {
            link.classList.add("active");
        }
        // Hoặc kiểm tra URL có chứa đường dẫn không (cho các trang con)
        else if (
            currentPath.startsWith(linkPath) &&
            linkPath !== "/" &&
            linkPath !== "/logout"
        ) {
            link.classList.add("active");
        }
    });
});
function navSearch() {
    const kw = document.getElementById("searchInputNav").value.trim();
    window.location.href =
        "/products" + (kw ? "?keyword=" + encodeURIComponent(kw) : "");
}
