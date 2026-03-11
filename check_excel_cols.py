import pandas as pd
file_path = r"c:\Users\mphat\Desktop\work\IE-Eff\info\EFF FEB V6.xlsx"
df = pd.read_excel(file_path, sheet_name="D", skiprows=2)
print("Columns in D sheet:")
for c in df.columns:
    print(c)
