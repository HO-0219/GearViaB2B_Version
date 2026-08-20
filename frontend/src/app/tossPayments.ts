declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      payment: (options: { customerKey: string }) => {
        requestBillingAuth: (options: {
          method: 'CARD'; successUrl: string; failUrl: string;
        }) => Promise<void>;
      };
    };
  }
}

export function loadTossSdk() {
  if (window.TossPayments) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://js.tosspayments.com/v2/standard';
    script.onload = () => resolve();
    script.onerror = () => reject({ message: '토스페이먼츠 SDK를 불러오지 못했습니다.' });
    document.head.appendChild(script);
  });
}
