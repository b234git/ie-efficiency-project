import openpyxl
wb = openpyxl.load_workbook('info/EFF FEB V6.xlsx', data_only=True)
sheet = wb['D']
print('H8 (DL):', sheet['H8'].value)
print('I8 (DLI):', sheet['I8'].value)
print('J8 (IDL):', sheet['J8'].value)
print('K8 (Output):', sheet['K8'].value)
print('BO8 (Total Quota):', sheet['BO8'].value)
print('CE8 (Total MP):', sheet['CE8'].value)
