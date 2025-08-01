document.addEventListener('DOMContentLoaded', () => {
    const listView = document.getElementById('list-view');
    const detailView = document.getElementById('detail-view');
    const viewContainer = document.getElementById('view-container');
    const biddersListTemplate = document.getElementById('bidders-list-template');

    let currentRequestId = null;

    async function renderBidders(requestId) {
        currentRequestId = requestId;
        try {
            const response = await fetch(`/api/fwd/my-posted-requests/${requestId}/bidders`);
            if (!response.ok) throw new Error('입찰자 정보를 불러오지 못했습니다.');

            const bidders = await response.json();
            
            detailView.innerHTML = '';
            const listClone = biddersListTemplate.content.cloneNode(true);
            const tableBody = listClone.querySelector('tbody');

            if (bidders.length === 0) {
                tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;">아직 입찰자가 없습니다.</td></tr>';
            } else {
                bidders.forEach(bid => {
                    const row = tableBody.insertRow();
                    row.innerHTML = `
                        <td>${bid.bidderCompanyName || '정보없음'}</td>
                        <td>${bid.containerId}</td>
                        <td>${bid.price.toLocaleString()} ${bid.currency}</td>
                        <td>${bid.etd}</td>
                        <td>${bid.eta}</td>
                        <td><button class="btn btn-sm btn-primary btn-confirm-bid" data-offer-id="${bid.offerId}">확정</button></td>
                    `;
                });
            }

            listClone.querySelector('.btn-back').addEventListener('click', showListView);
            detailView.appendChild(listClone);
            showDetailView();

        } catch (error) {
            console.error("Error fetching bidders:", error);
            detailView.innerHTML = `<p class="error-message">${error.message}</p>`;
            showDetailView();
        }
    }

    async function confirmBid(offerId) {
        if (!currentRequestId) return alert('요청 ID를 찾을 수 없습니다.');
        
        if (confirm(`이 제안을 최종 확정하시겠습니까?\n다른 모든 제안은 거절 처리됩니다.`)) {
            try {
                const response = await fetch(`/api/fwd/my-posted-requests/${currentRequestId}/confirm`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ offerId: offerId })
                });
                const message = await response.text();
                alert(message);
                if (response.ok) {
                    window.location.reload();
                }
            } catch (error) {
                console.error("Error confirming bid:", error);
                alert("낙찰 처리 중 오류가 발생했습니다.");
            }
        }
    }

    function showListView() {
        detailView.style.display = 'none';
        listView.style.display = 'block';
        currentRequestId = null;
    }

    function showDetailView() {
        listView.style.display = 'none';
        detailView.style.display = 'block';
    }

    viewContainer.addEventListener('click', e => {
        const detailsButton = e.target.closest('.btn-details');
        if (detailsButton) {
            const card = detailsButton.closest('.request-card');
            const requestId = card.dataset.requestId;
            renderBidders(requestId);
            return;
        }

        const confirmButton = e.target.closest('.btn-confirm-bid');
        if (confirmButton) {
            const offerId = confirmButton.dataset.offerId;
            confirmBid(offerId);
            return;
        }
    });
});