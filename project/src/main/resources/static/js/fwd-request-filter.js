document.addEventListener('DOMContentLoaded', () => {
    const portModal = document.getElementById('port-modal');
    const openModalBtn = document.getElementById('btn-open-port-modal');
    const closeModalBtn = portModal.querySelector('.btn-close');
    const applyBtn = document.getElementById('btn-apply-port-filter');
    const resetBtn = document.getElementById('btn-reset-port-filter');

    // 현재 URL의 모든 파라미터를 가져오는 헬퍼 함수
    const getCurrentParams = () => {
        return new URLSearchParams(window.location.search);
    };

    // 모달 열기
    openModalBtn.addEventListener('click', () => {
        portModal.style.display = 'flex';
    });

    // 모달 닫기 (X 버튼 또는 모달 바깥 클릭)
    const closeModal = () => {
        portModal.style.display = 'none';
    };
    closeModalBtn.addEventListener('click', closeModal);
    portModal.addEventListener('click', (e) => {
        if (e.target === portModal) {
            closeModal();
        }
    });

    // 초기화 버튼
    resetBtn.addEventListener('click', () => {
        const params = getCurrentParams();
        params.delete('departurePort');
        params.delete('arrivalPort');
        window.location.search = params.toString();
    });

    // 적용 버튼
    applyBtn.addEventListener('click', () => {
        const params = getCurrentParams();
        const selectedDeparture = document.querySelector('input[name="departure-port"]:checked');
        const selectedArrival = document.querySelector('input[name="arrival-port"]:checked');

        if (selectedDeparture) {
            params.set('departurePort', selectedDeparture.value);
        } else {
            params.delete('departurePort');
        }

        if (selectedArrival) {
            params.set('arrivalPort', selectedArrival.value);
        } else {
            params.delete('arrivalPort');
        }

        // 페이지 파라미터는 1페이지(0)로 초기화
        params.set('page', '0');
        
        // 새로운 파라미터로 페이지 이동
        window.location.search = params.toString();
    });
});