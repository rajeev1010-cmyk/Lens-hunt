import re
import json

def parse():
    with open('prompt.txt', 'r') as f:
        text = f.read()

    # We will use regex to find all character entries
    pattern = re.compile(r'id:\s*"([^"]+)",\s*name:\s*"([^"]+)",.*?profile:\s*axes\(\{([^}]+)\}\)', re.DOTALL)
    
    matches = pattern.findall(text)
    
    characters = []
    for match in matches:
        id_val = match[0]
        name_val = match[1]
        axes_str = match[2]
        
        # parse axes
        axes = {}
        # remove newlines
        axes_str = axes_str.replace('\n', ' ')
        axes_parts = axes_str.split(',')
        for p in axes_parts:
            p = p.strip()
            if ':' in p:
                k, v = p.split(':')
                k = k.strip()
                v = float(v.strip())
                axes[k] = v
        
        characters.append({
            'id': id_val,
            'name': name_val,
            'axes': axes
        })
        
    with open('app/src/main/assets/characters.json', 'w') as f:
        json.dump(characters, f, indent=2)
    print(f"Parsed {len(characters)} characters.")

if __name__ == '__main__':
    parse()
