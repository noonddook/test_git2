document.addEventListener('DOMContentLoaded', () => {
    const requestList = document.querySelector('.request-list');
    const offerFormTemplate = document.getElementById('offer-form-template');
    const currentUserId = document.querySelector('.container')?.dataset.currentUserId;

    if (!requestList || !offerFormTemplate) {
        console.error("필수 요소를 찾을 수 없습니다: .request-list 또는 #offer-form-template");
        return;
    }

    const closeOpenOfferForm = () => {
        const openForm = document.querySelector('.offer-form-expand');
        if (openForm) {
            const card = openForm.previousElementSibling;
            if (card?.classList.contains('is-expanded')) {
                card.classList.remove('is-expanded');
            }
            openForm.remove();
        }
    };

    requestList.addEventListener('click', async (e) => {
        if (!e.target.classList.contains('btn-quote')) {
            return;
        }

        const card = e.target.closest('.request-card');

        if (card.classList.contains('is-expanded')) {
            closeOpenOfferForm();
            return;
        }
        closeOpenOfferForm();

        const hasMyOffer = card.dataset.hasMyOffer === 'true';
        const requesterId = card.dataset.requesterId;
        
        let message = '';
        let shouldBlock = false;

        if (requesterId === currentUserId) {
            message = '본인이 등록한 재판매 요청입니다.';
            shouldBlock = true;
        } else if (hasMyOffer) {
            message = '이미 제안을 완료한 요청입니다.';
            shouldBlock = true;
        }

        if (shouldBlock) {
            const formClone = offerFormTemplate.content.cloneNode(true);
            const formWrapper = formClone.querySelector('.offer-form-expand');
            
            formWrapper.querySelector('.form-main-column').style.display = 'none';
            formWrapper.querySelector('.form-info-column .info-box').innerHTML = `
                <p class="form-status" style="font-weight: bold;">${message}</p>
                <div class="form-actions"><button class="btn btn-cancel">닫기</button></div>
            `;
            
            card.after(formWrapper);
            card.classList.add('is-expanded');
            formWrapper.querySelector('.btn-cancel').addEventListener('click', closeOpenOfferForm);
            return;
        }

        const requestId = card.dataset.requestId;
        const requestCbm = parseFloat(card.dataset.requestCbm);
        const requestDeadlineString = card.dataset.deadlineDatetime.substring(0, 10);

        try {
            const response = await fetch(`/api/fwd/available-containers?requestId=${requestId}`);
            if (!response.ok) throw new Error('서버에서 컨테이너 목록을 가져오는데 실패했습니다.');
            
            const availableContainers = await response.json();
            const formClone = offerFormTemplate.content.cloneNode(true);
            const formWrapper = formClone.querySelector('.offer-form-expand');
            
            const containerSelect = formWrapper.querySelector('.container-select');
            const capacityInput = formWrapper.querySelector('.available-capacity');
            const priceInput = formWrapper.querySelector('.bid-price');
            const currencySelect = formWrapper.querySelector('.currency-select');
            const statusText = formWrapper.querySelector('.form-status');
            const submitBtn = formWrapper.querySelector('.btn-submit-bid');
            const cancelBtn = formWrapper.querySelector('.btn-cancel');

            if (availableContainers.length > 0) {
                availableContainers.forEach(c => {
                    const option = document.createElement('option');
                    option.value = c.containerId;
                    option.textContent = c.containerDisplayName;
                    option.dataset.availableCbm = c.availableCbm;
                    option.dataset.etd = c.etd;
                    containerSelect.appendChild(option);
                });
            } else {
                statusText.innerHTML = `<strong class="impossible">제안 가능한 컨테이너 없음</strong>`;
            }

            containerSelect.addEventListener('change', () => {
                const selectedOption = containerSelect.options[containerSelect.selectedIndex];
                if (!selectedOption.value) {
                    capacityInput.value = '컨테이너 선택 시 자동 입력';
                    statusText.textContent = '';
                    submitBtn.disabled = true;
                    return;
                }
                
                const availableCbm = parseFloat(selectedOption.dataset.availableCbm);
                const containerEtdString = selectedOption.dataset.etd;
                capacityInput.value = `${availableCbm.toFixed(2)} CBM`;

                const isCbmOk = availableCbm >= requestCbm;
                const isDateOk = containerEtdString <= requestDeadlineString;

                if (isCbmOk && isDateOk) {
                    statusText.innerHTML = `<strong class="possible">견적제안 가능</strong>합니다.`;
                    submitBtn.disabled = false;
                } else {
                    let reason = !isCbmOk ? '잔여 용량이 부족합니다.' : '출항일이 요청 마감일보다 늦습니다.';
                    statusText.innerHTML = `<strong class="impossible">제안 불가</strong> (${reason})`;
                    submitBtn.disabled = true;
                }
            });

            cancelBtn.addEventListener('click', closeOpenOfferForm);

            submitBtn.addEventListener('click', async () => {
                if (!priceInput.value || priceInput.value <= 0 || !containerSelect.value) {
                    alert('컨테이너 선택 및 입찰가를 정확히 입력해주세요.');
                    return;
                }
                const offerData = {
                    requestId: requestId,
                    containerId: containerSelect.value,
                    price: priceInput.value,
                    currency: currencySelect.value
                };
                try {
                    const submitResponse = await fetch('/api/fwd/offers', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(offerData)
                    });
                    const message = await submitResponse.text();
                    alert(message);
                    if (submitResponse.ok) {
                        window.location.href = '/fwd/my-offers';
                    }
                } catch (error) {
                    console.error('Submit error:', error);
                    alert('제안 제출 중 오류가 발생했습니다.');
                }
            });
            
            card.after(formWrapper);
            card.classList.add('is-expanded');

        } catch (error) {
            console.error('Fetch error:', error);
            alert(error.message);
        }
    });
});