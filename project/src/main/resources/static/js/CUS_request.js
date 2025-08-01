// [✅ CUS_request.js 파일 전체를 이 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    
    // --- 1. 신규 요청 모달 관련 로직 ---
    const modal = document.getElementById('request-modal');
    const openModalBtn = document.getElementById('open-request-modal');
    
    if (modal && openModalBtn) {
        const closeModalBtn = modal.querySelector('.btn-close');
        const cancelBtn = modal.querySelector('.btn-cancel');
        const requestForm = document.getElementById('new-request-form');

        const openModal = () => modal.style.display = 'flex';
        const closeModal = () => {
            requestForm.reset();
            modal.style.display = 'none';
        };

        openModalBtn.addEventListener('click', openModal);
        closeModalBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);

        requestForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const formData = {
                itemName: document.getElementById('itemName').value,
                incoterms: document.getElementById('incoterms').value,
                totalCbm: parseFloat(document.getElementById('totalCbm').value),
                isDangerous: document.getElementById('isDangerous').checked,
                departurePort: document.getElementById('departurePort').value,
                arrivalPort: document.getElementById('arrivalPort').value,
                deadline: document.getElementById('deadline').value,
                tradeType: document.getElementById('tradeType').value,
                transportType: document.getElementById('transportType').value,
            };

            try {
                const response = await fetch('/api/cus/requests', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(formData)
                });

                const message = await response.text();
                alert(message);

                if (response.ok) {
                    closeModal();
                    location.reload();
                }
            } catch (error) {
                console.error('Error:', error);
                alert('요청 중 오류가 발생했습니다.');
            }
        });
    }

    // --- 2. 제안 보기 및 확정 관련 로직 ---
    const viewContainer = document.getElementById('view-container');
    const offerTemplate = document.getElementById('offer-list-template');
    let currentRequestId = null;

    // viewContainer가 없을 경우를 대비한 방어 코드
    if (!viewContainer || !offerTemplate) return;

    // 모든 이벤트는 viewContainer에 위임하여 처리
    viewContainer.addEventListener('click', async (e) => {
        const target = e.target;
        const requestCard = target.closest('.request-card');
        if (!requestCard) return; // 카드 외부 클릭은 무시

        const requestId = requestCard.dataset.requestId;
        currentRequestId = requestId; // 현재 작업중인 요청 ID 설정

        // '제안 보기' 버튼 클릭 처리
        if (target.classList.contains('btn-details')) {
            const detailsContainer = requestCard.querySelector('.offer-details-container');
            
            // 이미 열려있으면 닫기
            if (requestCard.classList.contains('is-expanded')) {
                detailsContainer.innerHTML = '';
                requestCard.classList.remove('is-expanded');
                return;
            }

            try {
                const response = await fetch(`/api/cus/requests/${requestId}/offers`);
                if (!response.ok) throw new Error('제안 정보를 불러오지 못했습니다.');
                const offers = await response.json();
                
                const templateClone = offerTemplate.content.cloneNode(true);
                const tableBody = templateClone.querySelector('tbody');

                if (offers.length === 0) {
                    tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;">도착한 제안이 없습니다.</td></tr>';
                } else {
                    offers.forEach(offer => {
                        const row = tableBody.insertRow();
                        row.innerHTML = `
                            <td>${offer.forwarderCompanyName}</td>
                            <td>${offer.containerId}</td>
                            <td>${offer.price.toLocaleString()} ${offer.currency}</td>
                            <td>${offer.etd}</td>
                            <td>${offer.eta}</td>
                            <td><button class="btn btn-sm btn-success btn-confirm-offer" data-offer-id="${offer.offerId}">확정</button></td>
                        `;
                    });
                }
                detailsContainer.innerHTML = '';
                detailsContainer.appendChild(templateClone);
                requestCard.classList.add('is-expanded');
            } catch (error) {
                console.error("Error fetching offers:", error);
                detailsContainer.innerHTML = `<p class="error-message" style="padding: 1rem; text-align: center;">${error.message}</p>`;
            }
        }

        // '확정' 버튼 클릭 처리 (동적으로 생성된 버튼)
        if (target.classList.contains('btn-confirm-offer')) {
            const winningOfferId = target.dataset.offerId;
            if (confirm('이 제안을 최종 확정하시겠습니까?')) {
                try {
                    const response = await fetch(`/api/cus/requests/${requestId}/confirm`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ winningOfferId: winningOfferId })
                    });
                    const message = await response.text();
                    alert(message);
                    if (response.ok) {
                        window.location.reload();
                    }
                } catch (error) {
                    alert("확정 처리 중 오류가 발생했습니다.");
                }
            }
        }
    });
});