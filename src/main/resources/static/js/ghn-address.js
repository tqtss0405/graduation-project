/**
 * ghn-address.js  — dùng chung cho checkout.html và profile.html
 * Proxy qua backend /api/ghn/* để tránh CORS
 */

const _ghnCache = {};

async function ghnGet(url) {
    if (_ghnCache[url]) return _ghnCache[url];
    try {
        const res = await fetch(url);
        if (!res.ok) {
            console.error('[GHN] HTTP', res.status, url);
            return { data: [] };
        }
        const json = await res.json();
        if (json.code !== 200) {
            console.error('[GHN] API error', json.code, json.message, url);
            return { data: [] };
        }
        _ghnCache[url] = json;
        return json;
    } catch (e) {
        console.error('[GHN] fetch error', e, url);
        return { data: [] };
    }
}

async function ghnLoadProvinces(selectEl) {
    const json = await ghnGet('/api/ghn/provinces');
    const list  = json.data || [];
    list.sort((a, b) => a.ProvinceName.localeCompare(b.ProvinceName, 'vi'));
    selectEl.innerHTML = '<option value="" disabled selected>-- Chọn Tỉnh/Thành --</option>';
    list.forEach(p => {
        const o = new Option(p.ProvinceName, p.ProvinceID);
        o.dataset.name = p.ProvinceName;
        selectEl.add(o);
    });
}

async function ghnLoadDistricts(provinceId, selectEl) {
    selectEl.innerHTML = '<option value="" disabled selected>-- Chọn Quận/Huyện --</option>';
    selectEl.disabled  = true;
    if (!provinceId) return;
    const json = await ghnGet('/api/ghn/districts?provinceId=' + provinceId);
    const list  = json.data || [];
    list.sort((a, b) => a.DistrictName.localeCompare(b.DistrictName, 'vi'));
    list.forEach(d => {
        const o = new Option(d.DistrictName, d.DistrictID);
        o.dataset.name = d.DistrictName;
        selectEl.add(o);
    });
    selectEl.disabled = false;
}

async function ghnLoadWards(districtId, selectEl) {
    selectEl.innerHTML = '<option value="" disabled selected>-- Chọn Phường/Xã --</option>';
    selectEl.disabled  = true;
    if (!districtId) return;
    const json = await ghnGet('/api/ghn/wards?districtId=' + districtId);
    const list  = json.data || [];
    list.sort((a, b) => a.WardName.localeCompare(b.WardName, 'vi'));
    list.forEach(w => {
        const o = new Option(w.WardName, w.WardCode);
        o.dataset.name = w.WardName;
        selectEl.add(o);
    });
    selectEl.disabled = false;
}

function ghnBuildFullAddress(streetVal, provinceEl, districtEl, wardEl) {
    const street   = (streetVal || '').trim();
    const provName = provinceEl.selectedIndex > 0
        ? provinceEl.options[provinceEl.selectedIndex].dataset.name : '';
    const distName = districtEl.selectedIndex > 0
        ? districtEl.options[districtEl.selectedIndex].dataset.name : '';
    const wardName = wardEl.selectedIndex > 0
        ? wardEl.options[wardEl.selectedIndex].dataset.name : '';
    return [street, wardName, distName, provName].filter(Boolean).join(', ');
}