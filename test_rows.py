import openpyxl

wb = openpyxl.load_workbook(r'c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx', data_only=True)
sheet = wb['D']

print("Row 375:")
row = sheet[375]
print(f"Date: {row[2].value}, Section: {row[3].value}, Line: {row[5].value}, Article: {row[13].value}, Output: {row[10].value}")

print("Row 395:")
row = sheet[395]
print(f"Date: {row[2].value}, Section: {row[3].value}, Line: {row[5].value}, Article: {row[13].value}, Output: {row[10].value}")
