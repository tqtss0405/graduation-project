// Preview product image
function previewImage(event) {
    const preview = event.target.parentElement;
    const file = event.target.files[0];

    if (file) {
        const reader = new FileReader();
        reader.onload = function (e) {
            preview.innerHTML = `<img src="${e.target.result}" alt="Preview">`;
        };
        reader.readAsDataURL(file);
    }
}

// Preview avatar
function previewAvatar(event) {
    const file = event.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = function (e) {
            document.getElementById("profileAvatarPreview").src =
                e.target.result;
        };
        reader.readAsDataURL(file);
    }
}

// Delete product
function deleteProduct(id) {
    if (confirm("Bạn có chắc chắn muốn xóa sản phẩm này?")) {
        // Call API to delete
        console.log("Deleting product:", id);
    }
}

// Form submission
document
    .getElementById("addProductForm")
    ?.addEventListener("submit", function (e) {
        e.preventDefault();
        alert("Thêm sản phẩm thành công!");
        bootstrap.Modal.getInstance(
            document.getElementById("addProductModal"),
        ).hide();
    });

document
    .getElementById("profileForm")
    ?.addEventListener("submit", function (e) {
        e.preventDefault();
        alert("Cập nhật thông tin thành công!");
        bootstrap.Modal.getInstance(
            document.getElementById("profileModal"),
        ).hide();
    });
