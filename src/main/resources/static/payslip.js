function exportPayslip() {
    const element = document.getElementById('payslip');
    const options = {
        margin: 1,
        filename: 'payslip.pdf',
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: { scale: 2 },
        jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' }
    };
    html2pdf().set(options).from(element).save();
}