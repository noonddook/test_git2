document.addEventListener('DOMContentLoaded', () => {
    const filterGroup = document.getElementById('transaction-filter-group');
    const tableBody = document.getElementById('transaction-list-body');

    // 데이터를 불러와 테이블을 렌더링하는 함수
    async function fetchAndRenderTransactions(filterType = 'all') {
        let url = '/api/fwd/transactions';
        if (filterType === 'sale') {
            url = '/api/fwd/transactions/sales';
        } else if (filterType === 'purchase') {
            url = '/api/fwd/transactions/purchases';
        }

        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error('데이터를 불러오는 데 실패했습니다.');

            const transactions = await response.json();
            tableBody.innerHTML = ''; // 테이블 비우기

            if (transactions.length === 0) {
                tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 40px 0; color: #6c757d;">거래 내역이 없습니다.</td></tr>';
                return;
            }

            transactions.forEach(tx => {
                const row = tableBody.insertRow();
                const transactionDate = new Date(tx.transactionDate).toLocaleDateString('ko-KR');
                const price = `${tx.price.toLocaleString()} ${tx.currency}`;

                // [디자인 개선 적용] 상태(Status)와 관리(버튼) 부분의 HTML 구조를 변경합니다.
				row.innerHTML = `
				    <td>${transactionDate}</td>
				    <td>${tx.type}</td>
				    <td>${tx.itemName}</td>
				    <td>${tx.partnerName || 'N/A'}</td>
				    <td>${price}</td>
				    <td>${tx.status}</td>
				    `;
            });
        } catch (error) {
            console.error(error);
            tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color: red; padding: 40px 0;">${error.message}</td></tr>`;
        }
    }

    // 필터 버튼 클릭 이벤트
    filterGroup.addEventListener('click', (e) => {
        // 클릭된 요소가 버튼이 아니면 아무것도 하지 않음
        if (e.target.tagName !== 'BUTTON') return;

        // 이미 활성화된 버튼을 다시 클릭하면 불필요한 재요청을 막음
        if (e.target.classList.contains('is-active')) return;

        // 기존에 활성화된 버튼의 'is-active' 클래스를 제거
        filterGroup.querySelector('.is-active').classList.remove('is-active');
        // 지금 클릭한 버튼에 'is-active' 클래스를 추가
        e.target.classList.add('is-active');

        const filterType = e.target.dataset.filter;
        fetchAndRenderTransactions(filterType);
    });

    // 페이지가 처음 로드될 때 '전체' 내역을 불러옴
    fetchAndRenderTransactions();
});