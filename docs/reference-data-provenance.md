# Reference data provenance

Where each bundled reference dataset's content came from, and the developer's review of it.

## Staples

- **Source:** The first 10 rows of Pantry's prior-build `staples.csv`, committed at `5b5bbdfa466c227be2d14e6e6acd3fabadb96e94` (2026-09-13, the commit immediately before the file's deletion in `e037454`), extended in T23 to the CSV's full 166 rows. That file's own header states its figures are "typical raw values from standard composition tables" for raw produce, meat and store-cupboard basics. No Open Food Facts or other ODbL-licensed data is used.
- **Retrieved:** 2026-09-27
- **Licence:** not stated by source
- **Reviewed:** 2026-09-28

### Nutrition database spot-check

- **Database:** McCance and Widdowson's Composition of Foods Integrated Dataset (CoFID), 2019 edition, published by Public Health England (`docs/cofid/CoFID_2019_label_nutrients.csv` and `docs/cofid/CoFID_2019_all_sheets.zip`, supplied by the developer; the implementer did not fetch either). Official source: gov.uk "Composition of Foods Integrated Dataset (CoFID)".
- **Database retrieved:** 2026-09-28
- **Database licence:** Open Government Licence — acknowledge CoFID / Public Health England as the source.
- **Note on methodology:** CoFID reports carbohydrate as "available carbohydrate" (monosaccharide equivalents, excluding fibre), which reads lower than a "carbohydrate by difference" figure for high-fibre foods (e.g. almonds, spinach) — a genuine methodology difference between sources, not a data error.
- **Developer decision:** every one of the 152 staples keys with a clear match in CoFID was updated to CoFID's figures (the developer's instruction: "take all of CoFID's as assumed correct, across the whole sheet"). 14 keys had no adequate CoFID match (a compound/prepared item, or a raw form CoFID does not carry) and were left at their original figures: chopped tomato, passata, soured cream, cream cheese, breadcrumb, caster sugar, maple syrup, vegetable stock, fish sauce, balsamic vinegar, dijon mustard, chicken breast, chicken thigh, chicken. Three CoFID carbohydrate figures (sugar, brown sugar, icing sugar) were capped at exactly 100 g — CoFID reports them fractionally over 100 g per 100 g, a measurement/rounding artefact for near-pure sugar, not a real value above the physical maximum.

| Key | Database ID | Database Entry Type | Pantry (kcal/protein/fat/carb) | Database (kcal/protein/fat/carb) | Action |
|---|---|---|---|---|---|
| almond | 14-896 | GA | 554/21.2/49.9/5.3 | 554/21.2/49.9/5.3 | Updated to database figure |
| anchovy | 16-448 | JC | 191/25.2/10/0 | 191/25.2/10.0/0.0 | Updated to database figure |
| apple | 14-319 | FA | 51/0.6/0.5/11.6 | 51/0.6/0.5/11.6 | Updated to database figure |
| asparagus | 13-157 | DG | 25/2.9/0.6/2 | 25/2.9/0.6/2.0 | Updated to database figure |
| aubergine | 13-161 | DG | 15/0.9/0.4/2.2 | 15/0.9/0.4/2.2 | Updated to database figure |
| avocado | 14-039 | FA | 189/2.1/19.3/1.9 | 189/2.1/19.3/1.9 | Updated to database figure |
| bacon | 19-646 | MAA | 136/18.8/6.7/0 | 136/18.8/6.7/0.0 | Updated to database figure |
| baked beans | 13-532 | DB | 81/5/0.5/15 | 81/5.0/0.5/15.0 | Updated to database figure |
| banana | 14-347 | FA | 51/0.8/0/12.8 | 51/0.8/Tr/12.8 | Updated to database figure |
| basil | 13-804 | H | 40/3.1/0.8/5.1 | 40/3.1/0.8/5.1 | Updated to database figure |
| basmati rice | 11-857 | AC | 351/8.1/0.5/83.7 | 351/8.1/0.5/83.7 | Updated to database figure |
| bay leaf | 13-806 | H | 313/7.6/8.4/48.6 | 313/7.6/8.4/48.6 | Updated to database figure |
| beef mince | 18-469 | MAC | 225/19.7/16.2/0 | 225/19.7/16.2/0.0 | Updated to database figure |
| beef steak | 18-084 | MAC | 116/23/2.7/0 | 116/23.0/2.7/0.0 | Updated to database figure |
| beetroot | 13-164 | DG | 36/1.7/0.1/7.6 | 36/1.7/0.1/7.6 | Updated to database figure |
| black bean | 13-062 | DB | 311/23.5/1.6/54.1 | 311/23.5/1.6/54.1 | Updated to database figure |
| black pepper | 13-880 | H | 0/10.4/3.3/0 | N/10.4/3.3/N | Updated to database figure |
| blueberry | 14-325 | FA | 40/0.9/0.2/9.1 | 40/0.9/0.2/9.1 | Updated to database figure |
| bread | 11-1145 | AF | 236/8.7/2.1/48.7 | 236/8.7/2.1/48.7 | Updated to database figure |
| broccoli | 13-502 | DG | 34/4.3/0.6/3.2 | 34/4.3/0.6/3.2 | Updated to database figure |
| brown rice | 11-866 | AC | 355/8.9/3.1/77.6 | 355/8.9/3.1/77.6 | Updated to database figure |
| brown sugar | 17-060 | SC | 380/0.1/0/100 | 380/0.1/0.0/101.3 | Updated to database figure |
| brussels sprout | 13-177 | DG | 42/3.5/1.4/4.1 | 42/3.5/1.4/4.1 | Updated to database figure |
| bulgur wheat | 11-904 | AA | 352/10.6/2/77.8 | 352/10.6/2.0/77.8 | Updated to database figure |
| butter | 17-685 | OA | 744/0.6/82.2/0.6 | 744/0.6/82.2/0.6 | Updated to database figure |
| butter bean | 13-070 | DB | 290/19.1/1.7/52.9 | 290/19.1/1.7/52.9 | Updated to database figure |
| butternut squash | 13-355 | DG | 36/1.1/0.1/8.3 | 36/1.1/0.1/8.3 | Updated to database figure |
| cabbage | 13-509 | DG | 24/1.2/0.1/4.8 | 24/1.2/0.1/4.8 | Updated to database figure |
| carrot | 13-496 | DG | 34/0.5/0.4/7.7 | 34/0.5/0.4/7.7 | Updated to database figure |
| cashew | 14-811 | GA | 573/17.7/48.2/18.1 | 573/17.7/48.2/18.1 | Updated to database figure |
| cauliflower | 13-512 | DG | 30/2.5/0.4/4.4 | 30/2.5/0.4/4.4 | Updated to database figure |
| celery | 13-636 | DG | 9/0.5/0.1/1.4 | 9/0.5/0.1/1.4 | Updated to database figure |
| cheddar | 12-346 | BL | 416/25.4/34.9/0.1 | 416/25.4/34.9/0.1 | Updated to database figure |
| cherry tomato | 13-519 | DG | 22/1.1/0.5/3.6 | 22/1.1/0.5/3.6 | Updated to database figure |
| chicken stock | 17-681 | WY | 12/2.3/0.2/0.2 | 12/2.3/0.2/0.2 | Updated to database figure |
| chickpea | 13-074 | DB | 320/21.3/5.4/49.6 | 320/21.3/5.4/49.6 | Updated to database figure |
| chilli | 13-317 | DG | 26/1.8/0.3/4.2 | 26/1.8/0.3/4.2 | Updated to database figure |
| chilli powder | 13-873 | H | 0/13.5/14.3/0 | N/13.5/14.3/N | Updated to database figure |
| chorizo | 19-516 | MI | 395/24/32.2/2.4 | 395/24.0/32.2/2.4 | Updated to database figure |
| cinnamon | 13-874 | H | 0/4/1.2/0 | N/4.0/1.2/N | Updated to database figure |
| cocoa powder | 12-545 | BH | 312/18.5/21.7/11.5 | 312/18.5/21.7/11.5 | Updated to database figure |
| coconut milk | 14-820 | GA | 22/0.3/0.3/4.9 | 22/0.3/0.3/4.9 | Updated to database figure |
| coconut oil | 17-031 | OC | 899/0/99.9/0 | 899/Tr/99.9/0.0 | Updated to database figure |
| cod | 16-372 | JA | 75/17.5/0.6/0 | 75/17.5/0.6/0.0 | Updated to database figure |
| coriander | 13-888 | H | 18/2.1/0.5/1.2 | 18/2.1/0.5/1.2 | Updated to database figure |
| coriander seed | 13-875 | H | 0/12.4/17.8/0 | N/12.4/17.8/N | Updated to database figure |
| courgette | 13-627 | DG | 16/1.3/0.2/2.3 | 16/1.3/0.2/2.3 | Updated to database figure |
| couscous | 11-901 | AT | 364/12/2.1/79.2 | 364/12.0/2.1/79.2 | Updated to database figure |
| creme fraiche | 12-335 | BJC | 378/2.2/40/2.4 | 378/2.2/40.0/2.4 | Updated to database figure |
| cucumber | 13-523 | DG | 14/1/0.6/1.2 | 14/1.0/0.6/1.2 | Updated to database figure |
| cumin | 13-889 | H | 0/17.8/22.3/0 | N/17.8/22.3/N | Updated to database figure |
| curry powder | 13-876 | H | 233/9.5/10.8/26.1 | 233/9.5/10.8/26.1 | Updated to database figure |
| dark chocolate | 17-491 | SEA | 510/5/28/63.5 | 510/5.0/28.0/63.5 | Updated to database figure |
| date | 14-083 | FA | 124/1.5/0.1/31.3 | 124/1.5/0.1/31.3 | Updated to database figure |
| double cream | 12-334 | BJC | 496/1.6/53.7/1.7 | 496/1.6/53.7/1.7 | Updated to database figure |
| egg | 12-937 | CA | 131/12.6/9/0 | 131/12.6/9.0/Tr | Updated to database figure |
| egg white | 12-938 | CA | 43/10.8/0/0 | 43/10.8/Tr/Tr | Updated to database figure |
| feta | 12-525 | BL | 250/15.6/20.2/1.5 | 250/15.6/20.2/1.5 | Updated to database figure |
| garlic | 13-244 | DG | 98/7.9/0.6/16.3 | 98/7.9/0.6/16.3 | Updated to database figure |
| ginger | 13-890 | H | 44/1.8/0.8/8.1 | 44/1.8/0.8/8.1 | Updated to database figure |
| golden syrup | 17-065 | SC | 298/0.3/0/79 | 298/0.3/0.0/79.0 | Updated to database figure |
| greek yoghurt | 12-555 | BN | 133/5.7/10.2/4.8 | 133/5.7/10.2/4.8 | Updated to database figure |
| green bean | 13-514 | DB | 24/2.1/0.4/3.1 | 24/2.1/0.4/3.1 | Updated to database figure |
| halloumi | 12-496 | BL | 313/23.9/23.5/1.7 | 313/23.9/23.5/1.7 | Updated to database figure |
| ham | 19-020 | MAA | 138/17.5/7.5/0 | 138/17.5/7.5/0.0 | Updated to database figure |
| honey | 17-050 | SC | 288/0.4/0/76.4 | 288/0.4/0.0/76.4 | Updated to database figure |
| icing sugar | 17-062 | SC | 393/0/0/100 | 393/Tr/0.0/104.9 | Updated to database figure |
| kale | 13-234 | DG | 33/3.4/1.6/1.4 | 33/3.4/1.6/1.4 | Updated to database figure |
| ketchup | 17-709 | WCN | 115/1.6/0.1/28.6 | 115/1.6/0.1/28.6 | Updated to database figure |
| kidney bean | 13-109 | DB | 266/22.1/1.4/44.1 | 266/22.1/1.4/44.1 | Updated to database figure |
| lamb | 18-478 | MAE | 187/19/12.3/0 | 187/19.0/12.3/0.0 | Updated to database figure |
| leek | 13-624 | DG | 23/1.5/0.2/4.1 | 23/1.5/0.2/4.1 | Updated to database figure |
| lemon | 14-130 | FA | 9/0.5/0.2/1.4 | 9/0.5/0.2/1.4 | Updated to database figure |
| lentil | 13-089 | DB | 297/24.3/1.9/48.8 | 297/24.3/1.9/48.8 | Updated to database figure |
| lettuce | 13-520 | DG | 11/1.2/0.1/1.4 | 11/1.2/0.1/1.4 | Updated to database figure |
| lime | 14-131 | FA | 9/0.7/0.3/0.8 | 9/0.7/0.3/0.8 | Updated to database figure |
| mackerel | 16-393 | JC | 233/18/17.9/0 | 233/18.0/17.9/0.0 | Updated to database figure |
| mango | 13-273 | DG | 46/0.5/0.2/11.2 | 46/0.5/0.2/11.2 | Updated to database figure |
| mayonnaise | 17-809 | WC | 786/1.7/84.2/0.1 | 786/1.7/84.2/0.1 | Updated to database figure |
| milk | 12-596 | BAK | 63/3.4/3.6/4.6 | 63/3.4/3.6/4.6 | Updated to database figure |
| mint | 13-836 | H | 43/3.8/0.7/5.3 | 43/3.8/0.7/5.3 | Updated to database figure |
| mozzarella | 12-360 | BL | 257/18.6/20.3/0 | 257/18.6/20.3/Tr | Updated to database figure |
| mushroom | 13-505 | DG | 7/1/0.2/0.3 | 7/1.0/0.2/0.3 | Updated to database figure |
| mustard | 17-363 | WY | 226/14.5/14.4/10.4 | 226/14.5/14.4/10.4 | Updated to database figure |
| noodle | 11-719 | AD | 338/12/2/72.6 | 338/12.0/2.0/72.6 | Updated to database figure |
| oats | 11-788 | A | 381/10.9/8.1/70.7 | 381/10.9/8.1/70.7 | Updated to database figure |
| olive oil | 17-038 | OC | 899/0/99.9/0 | 899/Tr/99.9/0.0 | Updated to database figure |
| onion | 13-499 | DG | 35/1/0.1/8 | 35/1.0/0.1/8.0 | Updated to database figure |
| orange | 14-327 | FA | 36/0.8/0.2/8.2 | 36/0.8/0.2/8.2 | Updated to database figure |
| oregano | 13-878 | H | 0/9/4.3/0 | N/9.0/4.3/N | Updated to database figure |
| paprika | 13-879 | H | 0/14.1/12.9/0 | N/14.1/12.9/N | Updated to database figure |
| parmesan | 12-526 | BL | 415/36.2/29.7/0.9 | 415/36.2/29.7/0.9 | Updated to database figure |
| parsley | 13-844 | H | 34/3/1.3/2.7 | 34/3.0/1.3/2.7 | Updated to database figure |
| parsnip | 13-312 | DG | 64/1.8/1.1/12.5 | 64/1.8/1.1/12.5 | Updated to database figure |
| pasta | 11-716 | AD | 343/11.3/1.6/75.6 | 343/11.3/1.6/75.6 | Updated to database figure |
| pea | 13-527 | DF | 68/5.3/0.7/10.7 | 68/5.3/0.7/10.7 | Updated to database figure |
| peanut butter | 14-892 | GA | 607/22.8/51.8/13.1 | 607/22.8/51.8/13.1 | Updated to database figure |
| pepper | 13-316 | DG | 20/2.9/0.6/0.7 | 20/2.9/0.6/0.7 | Updated to database figure |
| pine nut | 14-839 | GA | 688/14/68.6/4 | 688/14.0/68.6/4.0 | Updated to database figure |
| plain flour | 11-886 | AA | 352/9.1/1.4/80.9 | 352/9.1/1.4/80.9 | Updated to database figure |
| pork | 18-235 | MAG | 107/21.7/2.2/0 | 107/21.7/2.2/0.0 | Updated to database figure |
| pork mince | 18-267 | MAG | 164/19.2/9.7/0 | 164/19.2/9.7/0.0 | Updated to database figure |
| potato | 13-489 | DAM | 82/1.9/0.1/19.6 | 82/1.9/0.1/19.6 | Updated to database figure |
| prawn | 16-387 | JK | 77/17.6/0.7/0 | 77/17.6/0.7/0.0 | Updated to database figure |
| pumpkin | 13-326 | DG | 13/0.7/0.2/2.2 | 13/0.7/0.2/2.2 | Updated to database figure |
| quinoa | 14-843 | GA | 309/13.8/5/55.7 | 309/13.8/5.0/55.7 | Updated to database figure |
| raisin | 14-393 | FA | 256/3/1/62.6 | 256/3/1/62.6 | Updated to database figure |
| rapeseed oil | 17-041 | OC | 899/0/99.9/0 | 899/Tr/99.9/0.0 | Updated to database figure |
| raspberry | 14-375 | FA | 25/0.8/0.3/5.1 | 25/0.8/0.3/5.1 | Updated to database figure |
| red lentil | 13-657 | DB | 311/25.6/1.8/51.2 | 311/25.6/1.8/51.2 | Updated to database figure |
| red onion | 13-499 | DG | 35/1/0.1/8 | 35/1.0/0.1/8.0 | Updated to database figure |
| red pepper | 13-524 | DG | 21/0.8/0.2/4.3 | 21/0.8/0.2/4.3 | Updated to database figure |
| red wine | 17-752 | QE | 76/0.1/0/0.2 | 76/0.1/0.0/0.2 | Updated to database figure |
| red wine vinegar | 17-339 | WY | 22/0.4/0/0.6 | 22/0.4/0.0/0.6 | Updated to database figure |
| rice | 11-861 | AC | 355/6.7/1/85.1 | 355/6.7/1.0/85.1 | Updated to database figure |
| rocket | 13-522 | DG | 18/3.6/0.4/0 | 18/3.6/0.4/Tr | Updated to database figure |
| rosemary | 13-892 | H | 99/1.4/4.4/13.5 | 99/1.4/4.4/13.5 | Updated to database figure |
| salmon | 16-356 | J | 217/20.4/15/0 | 217/20.4/15.0/0.0 | Updated to database figure |
| salt | 17-367 | WY | 0/0/0/0 | 0/0.0/0.0/0.0 | Updated to database figure |
| sausage | 19-510 | MI | 309/11.9/25/9.6 | 309/11.9/25.0/9.6 | Updated to database figure |
| self raising flour | 11-888 | AA | 348/8.9/1.5/79.6 | 348/8.9/1.5/79.6 | Updated to database figure |
| semi skimmed milk | 12-313 | BAH | 46/3.5/1.7/4.7 | 46/3.5/1.7/4.7 | Updated to database figure |
| sesame oil | 17-043 | OC | 898/0.2/99.7/0 | 898/0.2/99.7/0.0 | Updated to database figure |
| sesame seed | 14-844 | GA | 598/18.2/58/0.9 | 598/18.2/58.0/0.9 | Updated to database figure |
| shallot | 13-342 | DG | 20/1.5/0.2/3.3 | 20/1.5/0.2/3.3 | Updated to database figure |
| single cream | 12-332 | BJC | 193/3.3/19.1/2.2 | 193/3.3/19.1/2.2 | Updated to database figure |
| soy sauce | 17-721 | WCN | 79/3/0/17.9 | 79/3.0/Tr/17.9 | Updated to database figure |
| spaghetti | 11-716 | AD | 343/11.3/1.6/75.6 | 343/11.3/1.6/75.6 | Updated to database figure |
| spinach | 13-521 | DG | 16/2.6/0.6/0.2 | 16/2.6/0.6/0.2 | Updated to database figure |
| spring onion | 13-352 | DG | 23/2/0.5/3 | 23/2.0/0.5/3.0 | Updated to database figure |
| stock cube | 17-515 | WY | 0/16.8/9.2/0 | N/16.8/9.2/N | Updated to database figure |
| strawberry | 14-324 | FA | 30/0.6/0.5/6.1 | 30/0.6/0.5/6.1 | Updated to database figure |
| sugar | 17-063 | SC | 394/0/0/100 | 394/Tr/0.0/105.0 | Updated to database figure |
| sunflower oil | 17-045 | OC | 899/0/99.9/0 | 899/Tr/99.9/0.0 | Updated to database figure |
| sweet potato | 13-463 | DG | 87/1.2/0.3/21.3 | 87/1.2/0.3/21.3 | Updated to database figure |
| sweetcorn | 13-623 | DG | 36/2/1.1/4.8 | 36/2.0/1.1/4.8 | Updated to database figure |
| thyme | 13-893 | H | 95/3/2.5/15.1 | 95/3.0/2.5/15.1 | Updated to database figure |
| tofu | 13-570 | DB | 73/8.1/4.2/0.7 | 73/8.1/4.2/0.7 | Updated to database figure |
| tomato | 13-517 | DG | 14/0.5/0.1/3 | 14/0.5/0.1/3.0 | Updated to database figure |
| tomato puree | 13-531 | DG | 67/4.4/0.2/12.9 | 67/4.4/0.2/12.9 | Updated to database figure |
| tortilla | 11-925 | AF | 285/7.8/5.7/53.9 | 285/7.8/5.7/53.9 | Updated to database figure |
| tuna | 16-399 | JC | 107/25.2/0.7/0 | 107/25.2/0.7/0.0 | Updated to database figure |
| turkey | 18-350 | MCO | 105/22.6/1.6/0 | 105/22.6/1.6/0.0 | Updated to database figure |
| turmeric | 13-861 | H | 0/6.7/7/0 | N/6.7/7.0/N | Updated to database figure |
| vegetable oil | 17-686 | OC | 899/0/99.9/0 | 899/Tr/99.9/0.0 | Updated to database figure |
| walnut | 14-879 | GA | 688/14.7/68.5/3.3 | 688/14.7/68.5/3.3 | Updated to database figure |
| white wine | 17-755 | QE | 75/0.1/0/0.6 | 75/0.1/0.0/0.6 | Updated to database figure |
| white wine vinegar | 17-339 | WY | 22/0.4/0/0.6 | 22/0.4/0.0/0.6 | Updated to database figure |
| wholemeal bread | 11-981 | AF | 217/9.4/2.5/42 | 217/9.4/2.5/42.0 | Updated to database figure |
| wholemeal flour | 11-889 | AA | 327/11.6/2/69.9 | 327/11.6/2.0/69.9 | Updated to database figure |
| worcestershire sauce | 17-723 | WCN | 113/1.4/0.1/28.3 | 113/1.4/0.1/28.3 | Updated to database figure |
| yoghurt | 12-184 | BNE | 79/5.7/3/7.8 | 79/5.7/3.0/7.8 | Updated to database figure |

### Figures changed after the spot-check

| Key | Field | Old Value | New Value | Database ID |
|---|---|---|---|---|
| onion | energyKcal | 40 | 35 | 13-499 |
| onion | proteinG | 1.1 | 1 | 13-499 |
| onion | carbohydrateG | 9.3 | 8 | 13-499 |
| red onion | energyKcal | 40 | 35 | 13-499 |
| red onion | proteinG | 1.1 | 1 | 13-499 |
| red onion | carbohydrateG | 9.3 | 8 | 13-499 |
| spring onion | energyKcal | 32 | 23 | 13-352 |
| spring onion | proteinG | 1.8 | 2 | 13-352 |
| spring onion | fatG | 0.2 | 0.5 | 13-352 |
| spring onion | carbohydrateG | 7.3 | 3 | 13-352 |
| shallot | energyKcal | 72 | 20 | 13-342 |
| shallot | proteinG | 2.5 | 1.5 | 13-342 |
| shallot | fatG | 0.1 | 0.2 | 13-342 |
| shallot | carbohydrateG | 16.8 | 3.3 | 13-342 |
| garlic | energyKcal | 149 | 98 | 13-244 |
| garlic | proteinG | 6.4 | 7.9 | 13-244 |
| garlic | fatG | 0.5 | 0.6 | 13-244 |
| garlic | carbohydrateG | 33.1 | 16.3 | 13-244 |
| leek | energyKcal | 61 | 23 | 13-624 |
| leek | fatG | 0.3 | 0.2 | 13-624 |
| leek | carbohydrateG | 14.2 | 4.1 | 13-624 |
| carrot | energyKcal | 41 | 34 | 13-496 |
| carrot | proteinG | 0.9 | 0.5 | 13-496 |
| carrot | fatG | 0.2 | 0.4 | 13-496 |
| carrot | carbohydrateG | 9.6 | 7.7 | 13-496 |
| potato | energyKcal | 77 | 82 | 13-489 |
| potato | proteinG | 2.0 | 1.9 | 13-489 |
| potato | carbohydrateG | 17.5 | 19.6 | 13-489 |
| sweet potato | energyKcal | 86 | 87 | 13-463 |
| sweet potato | proteinG | 1.6 | 1.2 | 13-463 |
| sweet potato | fatG | 0.1 | 0.3 | 13-463 |
| sweet potato | carbohydrateG | 20.1 | 21.3 | 13-463 |
| parsnip | energyKcal | 75 | 64 | 13-312 |
| parsnip | proteinG | 1.2 | 1.8 | 13-312 |
| parsnip | fatG | 0.3 | 1.1 | 13-312 |
| parsnip | carbohydrateG | 18.0 | 12.5 | 13-312 |
| tomato | energyKcal | 18 | 14 | 13-517 |
| tomato | proteinG | 0.9 | 0.5 | 13-517 |
| tomato | fatG | 0.2 | 0.1 | 13-517 |
| tomato | carbohydrateG | 3.9 | 3 | 13-517 |
| cherry tomato | energyKcal | 18 | 22 | 13-519 |
| cherry tomato | proteinG | 0.9 | 1.1 | 13-519 |
| cherry tomato | fatG | 0.2 | 0.5 | 13-519 |
| cherry tomato | carbohydrateG | 3.9 | 3.6 | 13-519 |
| tomato puree | energyKcal | 82 | 67 | 13-531 |
| tomato puree | proteinG | 4.3 | 4.4 | 13-531 |
| tomato puree | fatG | 0.5 | 0.2 | 13-531 |
| tomato puree | carbohydrateG | 18.9 | 12.9 | 13-531 |
| pepper | energyKcal | 31 | 20 | 13-316 |
| pepper | proteinG | 1 | 2.9 | 13-316 |
| pepper | fatG | 0.3 | 0.6 | 13-316 |
| pepper | carbohydrateG | 6 | 0.7 | 13-316 |
| red pepper | energyKcal | 31 | 21 | 13-524 |
| red pepper | proteinG | 1 | 0.8 | 13-524 |
| red pepper | fatG | 0.3 | 0.2 | 13-524 |
| red pepper | carbohydrateG | 6 | 4.3 | 13-524 |
| courgette | energyKcal | 17 | 16 | 13-627 |
| courgette | proteinG | 1.2 | 1.3 | 13-627 |
| courgette | fatG | 0.3 | 0.2 | 13-627 |
| courgette | carbohydrateG | 3.1 | 2.3 | 13-627 |
| aubergine | energyKcal | 25 | 15 | 13-161 |
| aubergine | proteinG | 1 | 0.9 | 13-161 |
| aubergine | fatG | 0.2 | 0.4 | 13-161 |
| aubergine | carbohydrateG | 5.9 | 2.2 | 13-161 |
| mushroom | energyKcal | 22 | 7 | 13-505 |
| mushroom | proteinG | 3.1 | 1 | 13-505 |
| mushroom | fatG | 0.3 | 0.2 | 13-505 |
| mushroom | carbohydrateG | 3.3 | 0.3 | 13-505 |
| broccoli | proteinG | 2.8 | 4.3 | 13-502 |
| broccoli | fatG | 0.4 | 0.6 | 13-502 |
| broccoli | carbohydrateG | 6.6 | 3.2 | 13-502 |
| cauliflower | energyKcal | 25 | 30 | 13-512 |
| cauliflower | proteinG | 1.9 | 2.5 | 13-512 |
| cauliflower | fatG | 0.3 | 0.4 | 13-512 |
| cauliflower | carbohydrateG | 5 | 4.4 | 13-512 |
| cabbage | energyKcal | 25 | 24 | 13-509 |
| cabbage | proteinG | 1.3 | 1.2 | 13-509 |
| cabbage | carbohydrateG | 5.8 | 4.8 | 13-509 |
| kale | energyKcal | 49 | 33 | 13-234 |
| kale | proteinG | 4.3 | 3.4 | 13-234 |
| kale | fatG | 0.9 | 1.6 | 13-234 |
| kale | carbohydrateG | 8.8 | 1.4 | 13-234 |
| spinach | energyKcal | 23 | 16 | 13-521 |
| spinach | proteinG | 2.9 | 2.6 | 13-521 |
| spinach | fatG | 0.4 | 0.6 | 13-521 |
| spinach | carbohydrateG | 3.6 | 0.2 | 13-521 |
| rocket | energyKcal | 25 | 18 | 13-522 |
| rocket | proteinG | 2.6 | 3.6 | 13-522 |
| rocket | fatG | 0.7 | 0.4 | 13-522 |
| rocket | carbohydrateG | 3.7 | 0 | 13-522 |
| lettuce | energyKcal | 15 | 11 | 13-520 |
| lettuce | proteinG | 1.4 | 1.2 | 13-520 |
| lettuce | fatG | 0.2 | 0.1 | 13-520 |
| lettuce | carbohydrateG | 2.9 | 1.4 | 13-520 |
| cucumber | energyKcal | 15 | 14 | 13-523 |
| cucumber | proteinG | 0.7 | 1 | 13-523 |
| cucumber | fatG | 0.1 | 0.6 | 13-523 |
| cucumber | carbohydrateG | 3.6 | 1.2 | 13-523 |
| celery | energyKcal | 16 | 9 | 13-636 |
| celery | proteinG | 0.7 | 0.5 | 13-636 |
| celery | fatG | 0.2 | 0.1 | 13-636 |
| celery | carbohydrateG | 3 | 1.4 | 13-636 |
| green bean | energyKcal | 31 | 24 | 13-514 |
| green bean | proteinG | 1.8 | 2.1 | 13-514 |
| green bean | fatG | 0.1 | 0.4 | 13-514 |
| green bean | carbohydrateG | 7 | 3.1 | 13-514 |
| pea | energyKcal | 81 | 68 | 13-527 |
| pea | proteinG | 5.4 | 5.3 | 13-527 |
| pea | fatG | 0.4 | 0.7 | 13-527 |
| pea | carbohydrateG | 14.5 | 10.7 | 13-527 |
| sweetcorn | energyKcal | 86 | 36 | 13-623 |
| sweetcorn | proteinG | 3.3 | 2 | 13-623 |
| sweetcorn | fatG | 1.4 | 1.1 | 13-623 |
| sweetcorn | carbohydrateG | 19 | 4.8 | 13-623 |
| butternut squash | energyKcal | 45 | 36 | 13-355 |
| butternut squash | proteinG | 1 | 1.1 | 13-355 |
| butternut squash | carbohydrateG | 11.7 | 8.3 | 13-355 |
| pumpkin | energyKcal | 26 | 13 | 13-326 |
| pumpkin | proteinG | 1 | 0.7 | 13-326 |
| pumpkin | fatG | 0.1 | 0.2 | 13-326 |
| pumpkin | carbohydrateG | 6.5 | 2.2 | 13-326 |
| beetroot | energyKcal | 43 | 36 | 13-164 |
| beetroot | proteinG | 1.6 | 1.7 | 13-164 |
| beetroot | fatG | 0.2 | 0.1 | 13-164 |
| beetroot | carbohydrateG | 9.6 | 7.6 | 13-164 |
| asparagus | energyKcal | 20 | 25 | 13-157 |
| asparagus | proteinG | 2.2 | 2.9 | 13-157 |
| asparagus | fatG | 0.1 | 0.6 | 13-157 |
| asparagus | carbohydrateG | 3.9 | 2 | 13-157 |
| brussels sprout | energyKcal | 43 | 42 | 13-177 |
| brussels sprout | proteinG | 3.4 | 3.5 | 13-177 |
| brussels sprout | fatG | 0.3 | 1.4 | 13-177 |
| brussels sprout | carbohydrateG | 9 | 4.1 | 13-177 |
| chilli | energyKcal | 40 | 26 | 13-317 |
| chilli | proteinG | 1.9 | 1.8 | 13-317 |
| chilli | fatG | 0.4 | 0.3 | 13-317 |
| chilli | carbohydrateG | 8.8 | 4.2 | 13-317 |
| ginger | energyKcal | 80 | 44 | 13-890 |
| ginger | carbohydrateG | 17.8 | 8.1 | 13-890 |
| lemon | energyKcal | 29 | 9 | 14-130 |
| lemon | proteinG | 1.1 | 0.5 | 14-130 |
| lemon | fatG | 0.3 | 0.2 | 14-130 |
| lemon | carbohydrateG | 9.3 | 1.4 | 14-130 |
| lime | energyKcal | 30 | 9 | 14-131 |
| lime | fatG | 0.2 | 0.3 | 14-131 |
| lime | carbohydrateG | 10.5 | 0.8 | 14-131 |
| orange | energyKcal | 47 | 36 | 14-327 |
| orange | proteinG | 0.9 | 0.8 | 14-327 |
| orange | fatG | 0.1 | 0.2 | 14-327 |
| orange | carbohydrateG | 11.8 | 8.2 | 14-327 |
| apple | energyKcal | 52 | 51 | 14-319 |
| apple | proteinG | 0.3 | 0.6 | 14-319 |
| apple | fatG | 0.2 | 0.5 | 14-319 |
| apple | carbohydrateG | 13.8 | 11.6 | 14-319 |
| banana | energyKcal | 89 | 51 | 14-347 |
| banana | proteinG | 1.1 | 0.8 | 14-347 |
| banana | fatG | 0.3 | 0 | 14-347 |
| banana | carbohydrateG | 22.8 | 12.8 | 14-347 |
| avocado | energyKcal | 160 | 189 | 14-039 |
| avocado | proteinG | 2 | 2.1 | 14-039 |
| avocado | fatG | 14.7 | 19.3 | 14-039 |
| avocado | carbohydrateG | 8.5 | 1.9 | 14-039 |
| strawberry | energyKcal | 32 | 30 | 14-324 |
| strawberry | proteinG | 0.7 | 0.6 | 14-324 |
| strawberry | fatG | 0.3 | 0.5 | 14-324 |
| strawberry | carbohydrateG | 7.7 | 6.1 | 14-324 |
| blueberry | energyKcal | 57 | 40 | 14-325 |
| blueberry | proteinG | 0.7 | 0.9 | 14-325 |
| blueberry | fatG | 0.3 | 0.2 | 14-325 |
| blueberry | carbohydrateG | 14.5 | 9.1 | 14-325 |
| raspberry | energyKcal | 52 | 25 | 14-375 |
| raspberry | proteinG | 1.2 | 0.8 | 14-375 |
| raspberry | fatG | 0.7 | 0.3 | 14-375 |
| raspberry | carbohydrateG | 11.9 | 5.1 | 14-375 |
| mango | energyKcal | 60 | 46 | 13-273 |
| mango | proteinG | 0.8 | 0.5 | 13-273 |
| mango | fatG | 0.4 | 0.2 | 13-273 |
| mango | carbohydrateG | 15 | 11.2 | 13-273 |
| parsley | energyKcal | 36 | 34 | 13-844 |
| parsley | fatG | 0.8 | 1.3 | 13-844 |
| parsley | carbohydrateG | 6.3 | 2.7 | 13-844 |
| coriander | energyKcal | 23 | 18 | 13-888 |
| coriander | carbohydrateG | 3.7 | 1.2 | 13-888 |
| basil | energyKcal | 23 | 40 | 13-804 |
| basil | proteinG | 3.2 | 3.1 | 13-804 |
| basil | fatG | 0.6 | 0.8 | 13-804 |
| basil | carbohydrateG | 2.7 | 5.1 | 13-804 |
| mint | energyKcal | 44 | 43 | 13-836 |
| mint | proteinG | 3.3 | 3.8 | 13-836 |
| mint | carbohydrateG | 8.4 | 5.3 | 13-836 |
| thyme | energyKcal | 101 | 95 | 13-893 |
| thyme | proteinG | 5.6 | 3 | 13-893 |
| thyme | fatG | 1.7 | 2.5 | 13-893 |
| thyme | carbohydrateG | 24.5 | 15.1 | 13-893 |
| rosemary | energyKcal | 131 | 99 | 13-892 |
| rosemary | proteinG | 3.3 | 1.4 | 13-892 |
| rosemary | fatG | 5.9 | 4.4 | 13-892 |
| rosemary | carbohydrateG | 20.7 | 13.5 | 13-892 |
| beef mince | energyKcal | 254 | 225 | 18-469 |
| beef mince | proteinG | 17.2 | 19.7 | 18-469 |
| beef mince | fatG | 20 | 16.2 | 18-469 |
| beef steak | energyKcal | 217 | 116 | 18-084 |
| beef steak | proteinG | 26.1 | 23 | 18-084 |
| beef steak | fatG | 12.7 | 2.7 | 18-084 |
| lamb | energyKcal | 294 | 187 | 18-478 |
| lamb | proteinG | 25 | 19 | 18-478 |
| lamb | fatG | 21 | 12.3 | 18-478 |
| pork | energyKcal | 242 | 107 | 18-235 |
| pork | proteinG | 27.3 | 21.7 | 18-235 |
| pork | fatG | 14 | 2.2 | 18-235 |
| pork mince | energyKcal | 263 | 164 | 18-267 |
| pork mince | proteinG | 16.9 | 19.2 | 18-267 |
| pork mince | fatG | 21.2 | 9.7 | 18-267 |
| bacon | energyKcal | 541 | 136 | 19-646 |
| bacon | proteinG | 37 | 18.8 | 19-646 |
| bacon | fatG | 42 | 6.7 | 19-646 |
| bacon | carbohydrateG | 1.4 | 0 | 19-646 |
| sausage | energyKcal | 301 | 309 | 19-510 |
| sausage | proteinG | 12.3 | 11.9 | 19-510 |
| sausage | fatG | 24 | 25 | 19-510 |
| sausage | carbohydrateG | 9.9 | 9.6 | 19-510 |
| chorizo | energyKcal | 455 | 395 | 19-516 |
| chorizo | proteinG | 24.1 | 24 | 19-516 |
| chorizo | fatG | 38.3 | 32.2 | 19-516 |
| chorizo | carbohydrateG | 1.9 | 2.4 | 19-516 |
| ham | energyKcal | 145 | 138 | 19-020 |
| ham | proteinG | 20.9 | 17.5 | 19-020 |
| ham | fatG | 5.5 | 7.5 | 19-020 |
| ham | carbohydrateG | 1.5 | 0 | 19-020 |
| turkey | energyKcal | 157 | 105 | 18-350 |
| turkey | proteinG | 29 | 22.6 | 18-350 |
| turkey | fatG | 3.6 | 1.6 | 18-350 |
| salmon | energyKcal | 208 | 217 | 16-356 |
| salmon | fatG | 13.4 | 15 | 16-356 |
| tuna | energyKcal | 132 | 107 | 16-399 |
| tuna | proteinG | 28 | 25.2 | 16-399 |
| tuna | fatG | 1.3 | 0.7 | 16-399 |
| cod | energyKcal | 82 | 75 | 16-372 |
| cod | proteinG | 18 | 17.5 | 16-372 |
| cod | fatG | 0.7 | 0.6 | 16-372 |
| prawn | energyKcal | 99 | 77 | 16-387 |
| prawn | proteinG | 24 | 17.6 | 16-387 |
| prawn | fatG | 0.3 | 0.7 | 16-387 |
| prawn | carbohydrateG | 0.2 | 0 | 16-387 |
| mackerel | energyKcal | 205 | 233 | 16-393 |
| mackerel | proteinG | 18.6 | 18 | 16-393 |
| mackerel | fatG | 13.9 | 17.9 | 16-393 |
| anchovy | energyKcal | 131 | 191 | 16-448 |
| anchovy | proteinG | 20.4 | 25.2 | 16-448 |
| anchovy | fatG | 4.8 | 10 | 16-448 |
| egg | energyKcal | 143 | 131 | 12-937 |
| egg | fatG | 9.5 | 9 | 12-937 |
| egg | carbohydrateG | 0.7 | 0 | 12-937 |
| egg white | energyKcal | 52 | 43 | 12-938 |
| egg white | proteinG | 10.9 | 10.8 | 12-938 |
| egg white | fatG | 0.2 | 0 | 12-938 |
| egg white | carbohydrateG | 0.7 | 0 | 12-938 |
| milk | energyKcal | 64 | 63 | 12-596 |
| milk | carbohydrateG | 4.8 | 4.6 | 12-596 |
| semi skimmed milk | energyKcal | 50 | 46 | 12-313 |
| semi skimmed milk | proteinG | 3.6 | 3.5 | 12-313 |
| semi skimmed milk | fatG | 1.8 | 1.7 | 12-313 |
| semi skimmed milk | carbohydrateG | 4.8 | 4.7 | 12-313 |
| double cream | energyKcal | 449 | 496 | 12-334 |
| double cream | proteinG | 1.7 | 1.6 | 12-334 |
| double cream | fatG | 48 | 53.7 | 12-334 |
| double cream | carbohydrateG | 2.7 | 1.7 | 12-334 |
| single cream | proteinG | 2.6 | 3.3 | 12-332 |
| single cream | fatG | 18 | 19.1 | 12-332 |
| single cream | carbohydrateG | 4.1 | 2.2 | 12-332 |
| creme fraiche | energyKcal | 299 | 378 | 12-335 |
| creme fraiche | proteinG | 2.4 | 2.2 | 12-335 |
| creme fraiche | fatG | 30 | 40 | 12-335 |
| creme fraiche | carbohydrateG | 3 | 2.4 | 12-335 |
| yoghurt | energyKcal | 61 | 79 | 12-184 |
| yoghurt | proteinG | 3.5 | 5.7 | 12-184 |
| yoghurt | fatG | 3.3 | 3 | 12-184 |
| yoghurt | carbohydrateG | 4.7 | 7.8 | 12-184 |
| greek yoghurt | energyKcal | 97 | 133 | 12-555 |
| greek yoghurt | proteinG | 9 | 5.7 | 12-555 |
| greek yoghurt | fatG | 5 | 10.2 | 12-555 |
| greek yoghurt | carbohydrateG | 3.9 | 4.8 | 12-555 |
| butter | proteinG | 0.9 | 0.6 | 17-685 |
| parmesan | energyKcal | 431 | 415 | 12-526 |
| parmesan | proteinG | 38.5 | 36.2 | 12-526 |
| parmesan | carbohydrateG | 0 | 0.9 | 12-526 |
| mozzarella | energyKcal | 300 | 257 | 12-360 |
| mozzarella | proteinG | 22.2 | 18.6 | 12-360 |
| mozzarella | fatG | 22.4 | 20.3 | 12-360 |
| mozzarella | carbohydrateG | 2.2 | 0 | 12-360 |
| feta | energyKcal | 264 | 250 | 12-525 |
| feta | proteinG | 14.2 | 15.6 | 12-525 |
| feta | fatG | 21.3 | 20.2 | 12-525 |
| feta | carbohydrateG | 4.1 | 1.5 | 12-525 |
| halloumi | energyKcal | 321 | 313 | 12-496 |
| halloumi | proteinG | 22 | 23.9 | 12-496 |
| halloumi | fatG | 25 | 23.5 | 12-496 |
| halloumi | carbohydrateG | 2 | 1.7 | 12-496 |
| plain flour | energyKcal | 341 | 352 | 11-886 |
| plain flour | proteinG | 9.4 | 9.1 | 11-886 |
| plain flour | fatG | 1.3 | 1.4 | 11-886 |
| plain flour | carbohydrateG | 72 | 80.9 | 11-886 |
| self raising flour | energyKcal | 330 | 348 | 11-888 |
| self raising flour | fatG | 1.2 | 1.5 | 11-888 |
| self raising flour | carbohydrateG | 69 | 79.6 | 11-888 |
| wholemeal flour | energyKcal | 310 | 327 | 11-889 |
| wholemeal flour | proteinG | 12.6 | 11.6 | 11-889 |
| wholemeal flour | fatG | 2.2 | 2 | 11-889 |
| wholemeal flour | carbohydrateG | 61 | 69.9 | 11-889 |
| bread | energyKcal | 265 | 236 | 11-1145 |
| bread | proteinG | 9 | 8.7 | 11-1145 |
| bread | fatG | 3.2 | 2.1 | 11-1145 |
| bread | carbohydrateG | 49 | 48.7 | 11-1145 |
| wholemeal bread | energyKcal | 247 | 217 | 11-981 |
| wholemeal bread | proteinG | 10.7 | 9.4 | 11-981 |
| wholemeal bread | fatG | 3.4 | 2.5 | 11-981 |
| wholemeal bread | carbohydrateG | 41.3 | 42 | 11-981 |
| pasta | energyKcal | 352 | 343 | 11-716 |
| pasta | proteinG | 12.5 | 11.3 | 11-716 |
| pasta | fatG | 1.5 | 1.6 | 11-716 |
| pasta | carbohydrateG | 71 | 75.6 | 11-716 |
| spaghetti | energyKcal | 352 | 343 | 11-716 |
| spaghetti | proteinG | 12.5 | 11.3 | 11-716 |
| spaghetti | fatG | 1.5 | 1.6 | 11-716 |
| spaghetti | carbohydrateG | 71 | 75.6 | 11-716 |
| rice | energyKcal | 356 | 355 | 11-861 |
| rice | proteinG | 7.1 | 6.7 | 11-861 |
| rice | fatG | 0.6 | 1 | 11-861 |
| rice | carbohydrateG | 79 | 85.1 | 11-861 |
| brown rice | energyKcal | 362 | 355 | 11-866 |
| brown rice | proteinG | 7.5 | 8.9 | 11-866 |
| brown rice | fatG | 2.7 | 3.1 | 11-866 |
| brown rice | carbohydrateG | 76 | 77.6 | 11-866 |
| basmati rice | energyKcal | 356 | 351 | 11-857 |
| basmati rice | proteinG | 8.5 | 8.1 | 11-857 |
| basmati rice | fatG | 0.9 | 0.5 | 11-857 |
| basmati rice | carbohydrateG | 78 | 83.7 | 11-857 |
| couscous | energyKcal | 376 | 364 | 11-901 |
| couscous | proteinG | 12.8 | 12 | 11-901 |
| couscous | fatG | 0.6 | 2.1 | 11-901 |
| couscous | carbohydrateG | 77.4 | 79.2 | 11-901 |
| quinoa | energyKcal | 368 | 309 | 14-843 |
| quinoa | proteinG | 14.1 | 13.8 | 14-843 |
| quinoa | fatG | 6.1 | 5 | 14-843 |
| quinoa | carbohydrateG | 64.2 | 55.7 | 14-843 |
| bulgur wheat | energyKcal | 342 | 352 | 11-904 |
| bulgur wheat | proteinG | 12.3 | 10.6 | 11-904 |
| bulgur wheat | fatG | 1.3 | 2 | 11-904 |
| bulgur wheat | carbohydrateG | 75.9 | 77.8 | 11-904 |
| oats | energyKcal | 379 | 381 | 11-788 |
| oats | proteinG | 13.2 | 10.9 | 11-788 |
| oats | fatG | 6.5 | 8.1 | 11-788 |
| oats | carbohydrateG | 67.7 | 70.7 | 11-788 |
| noodle | energyKcal | 348 | 338 | 11-719 |
| noodle | proteinG | 11.5 | 12 | 11-719 |
| noodle | fatG | 1.8 | 2 | 11-719 |
| noodle | carbohydrateG | 71 | 72.6 | 11-719 |
| tortilla | energyKcal | 306 | 285 | 11-925 |
| tortilla | proteinG | 8 | 7.8 | 11-925 |
| tortilla | fatG | 7 | 5.7 | 11-925 |
| tortilla | carbohydrateG | 51 | 53.9 | 11-925 |
| lentil | energyKcal | 352 | 297 | 13-089 |
| lentil | proteinG | 24.6 | 24.3 | 13-089 |
| lentil | fatG | 1.1 | 1.9 | 13-089 |
| lentil | carbohydrateG | 63.4 | 48.8 | 13-089 |
| red lentil | energyKcal | 358 | 311 | 13-657 |
| red lentil | proteinG | 24 | 25.6 | 13-657 |
| red lentil | fatG | 1.5 | 1.8 | 13-657 |
| red lentil | carbohydrateG | 60 | 51.2 | 13-657 |
| chickpea | energyKcal | 364 | 320 | 13-074 |
| chickpea | proteinG | 19.3 | 21.3 | 13-074 |
| chickpea | fatG | 6 | 5.4 | 13-074 |
| chickpea | carbohydrateG | 61 | 49.6 | 13-074 |
| kidney bean | energyKcal | 333 | 266 | 13-109 |
| kidney bean | proteinG | 23.6 | 22.1 | 13-109 |
| kidney bean | fatG | 0.8 | 1.4 | 13-109 |
| kidney bean | carbohydrateG | 60 | 44.1 | 13-109 |
| black bean | energyKcal | 341 | 311 | 13-062 |
| black bean | proteinG | 21.6 | 23.5 | 13-062 |
| black bean | fatG | 1.4 | 1.6 | 13-062 |
| black bean | carbohydrateG | 62.4 | 54.1 | 13-062 |
| butter bean | energyKcal | 338 | 290 | 13-070 |
| butter bean | proteinG | 21 | 19.1 | 13-070 |
| butter bean | fatG | 1.2 | 1.7 | 13-070 |
| butter bean | carbohydrateG | 63.4 | 52.9 | 13-070 |
| baked beans | energyKcal | 94 | 81 | 13-532 |
| baked beans | proteinG | 4.8 | 5 | 13-532 |
| baked beans | fatG | 0.6 | 0.5 | 13-532 |
| tofu | energyKcal | 76 | 73 | 13-570 |
| tofu | fatG | 4.8 | 4.2 | 13-570 |
| tofu | carbohydrateG | 1.9 | 0.7 | 13-570 |
| olive oil | energyKcal | 884 | 899 | 17-038 |
| olive oil | fatG | 100 | 99.9 | 17-038 |
| vegetable oil | energyKcal | 884 | 899 | 17-686 |
| vegetable oil | fatG | 100 | 99.9 | 17-686 |
| rapeseed oil | energyKcal | 884 | 899 | 17-041 |
| rapeseed oil | fatG | 100 | 99.9 | 17-041 |
| sunflower oil | energyKcal | 884 | 899 | 17-045 |
| sunflower oil | fatG | 100 | 99.9 | 17-045 |
| sesame oil | energyKcal | 884 | 898 | 17-043 |
| sesame oil | proteinG | 0 | 0.2 | 17-043 |
| sesame oil | fatG | 100 | 99.7 | 17-043 |
| coconut oil | energyKcal | 892 | 899 | 17-031 |
| coconut oil | fatG | 99.1 | 99.9 | 17-031 |
| sugar | energyKcal | 400 | 394 | 17-063 |
| brown sugar | proteinG | 0 | 0.1 | 17-060 |
| brown sugar | carbohydrateG | 98.1 | 100 | 17-060 |
| icing sugar | carbohydrateG | 99 | 100 | 17-062 |
| honey | energyKcal | 304 | 288 | 17-050 |
| honey | proteinG | 0.3 | 0.4 | 17-050 |
| honey | carbohydrateG | 82.4 | 76.4 | 17-050 |
| golden syrup | energyKcal | 325 | 298 | 17-065 |
| black pepper | energyKcal | 251 | 0 | 13-880 |
| black pepper | carbohydrateG | 63.9 | 0 | 13-880 |
| paprika | energyKcal | 282 | 0 | 13-879 |
| paprika | carbohydrateG | 54 | 0 | 13-879 |
| cumin | energyKcal | 375 | 0 | 13-889 |
| cumin | carbohydrateG | 44.2 | 0 | 13-889 |
| coriander seed | energyKcal | 298 | 0 | 13-875 |
| coriander seed | carbohydrateG | 55 | 0 | 13-875 |
| turmeric | energyKcal | 354 | 0 | 13-861 |
| turmeric | proteinG | 7.8 | 6.7 | 13-861 |
| turmeric | fatG | 9.9 | 7 | 13-861 |
| turmeric | carbohydrateG | 64.9 | 0 | 13-861 |
| cinnamon | energyKcal | 247 | 0 | 13-874 |
| cinnamon | carbohydrateG | 80.6 | 0 | 13-874 |
| curry powder | energyKcal | 325 | 233 | 13-876 |
| curry powder | proteinG | 14.3 | 9.5 | 13-876 |
| curry powder | fatG | 14 | 10.8 | 13-876 |
| curry powder | carbohydrateG | 55.8 | 26.1 | 13-876 |
| chilli powder | energyKcal | 282 | 0 | 13-873 |
| chilli powder | carbohydrateG | 49.7 | 0 | 13-873 |
| oregano | energyKcal | 265 | 0 | 13-878 |
| oregano | carbohydrateG | 68.9 | 0 | 13-878 |
| bay leaf | carbohydrateG | 75 | 48.6 | 13-806 |
| stock cube | energyKcal | 240 | 0 | 17-515 |
| stock cube | proteinG | 12 | 16.8 | 17-515 |
| stock cube | fatG | 12 | 9.2 | 17-515 |
| stock cube | carbohydrateG | 20 | 0 | 17-515 |
| chicken stock | energyKcal | 7 | 12 | 17-681 |
| chicken stock | proteinG | 0.5 | 2.3 | 17-681 |
| chicken stock | carbohydrateG | 0.6 | 0.2 | 17-681 |
| soy sauce | energyKcal | 53 | 79 | 17-721 |
| soy sauce | proteinG | 8.1 | 3 | 17-721 |
| soy sauce | fatG | 0.6 | 0 | 17-721 |
| soy sauce | carbohydrateG | 4.9 | 17.9 | 17-721 |
| worcestershire sauce | energyKcal | 78 | 113 | 17-723 |
| worcestershire sauce | proteinG | 0 | 1.4 | 17-723 |
| worcestershire sauce | fatG | 0 | 0.1 | 17-723 |
| worcestershire sauce | carbohydrateG | 19.5 | 28.3 | 17-723 |
| white wine vinegar | energyKcal | 18 | 22 | 17-339 |
| white wine vinegar | proteinG | 0 | 0.4 | 17-339 |
| white wine vinegar | carbohydrateG | 0.4 | 0.6 | 17-339 |
| red wine vinegar | energyKcal | 19 | 22 | 17-339 |
| red wine vinegar | proteinG | 0 | 0.4 | 17-339 |
| red wine vinegar | carbohydrateG | 0.3 | 0.6 | 17-339 |
| mustard | energyKcal | 66 | 226 | 17-363 |
| mustard | proteinG | 4.4 | 14.5 | 17-363 |
| mustard | fatG | 3.3 | 14.4 | 17-363 |
| mustard | carbohydrateG | 5.8 | 10.4 | 17-363 |
| mayonnaise | energyKcal | 680 | 786 | 17-809 |
| mayonnaise | proteinG | 1 | 1.7 | 17-809 |
| mayonnaise | fatG | 75 | 84.2 | 17-809 |
| mayonnaise | carbohydrateG | 1.3 | 0.1 | 17-809 |
| ketchup | energyKcal | 102 | 115 | 17-709 |
| ketchup | proteinG | 1 | 1.6 | 17-709 |
| ketchup | carbohydrateG | 25.8 | 28.6 | 17-709 |
| coconut milk | energyKcal | 197 | 22 | 14-820 |
| coconut milk | proteinG | 2 | 0.3 | 14-820 |
| coconut milk | fatG | 21.3 | 0.3 | 14-820 |
| coconut milk | carbohydrateG | 2.8 | 4.9 | 14-820 |
| peanut butter | energyKcal | 588 | 607 | 14-892 |
| peanut butter | proteinG | 25.1 | 22.8 | 14-892 |
| peanut butter | fatG | 50.4 | 51.8 | 14-892 |
| peanut butter | carbohydrateG | 20 | 13.1 | 14-892 |
| almond | energyKcal | 579 | 554 | 14-896 |
| almond | carbohydrateG | 21.6 | 5.3 | 14-896 |
| walnut | energyKcal | 654 | 688 | 14-879 |
| walnut | proteinG | 15.2 | 14.7 | 14-879 |
| walnut | fatG | 65.2 | 68.5 | 14-879 |
| walnut | carbohydrateG | 13.7 | 3.3 | 14-879 |
| cashew | energyKcal | 553 | 573 | 14-811 |
| cashew | proteinG | 18.2 | 17.7 | 14-811 |
| cashew | fatG | 43.9 | 48.2 | 14-811 |
| cashew | carbohydrateG | 30.2 | 18.1 | 14-811 |
| pine nut | energyKcal | 673 | 688 | 14-839 |
| pine nut | proteinG | 13.7 | 14 | 14-839 |
| pine nut | fatG | 68.4 | 68.6 | 14-839 |
| pine nut | carbohydrateG | 13.1 | 4 | 14-839 |
| sesame seed | energyKcal | 573 | 598 | 14-844 |
| sesame seed | proteinG | 17.7 | 18.2 | 14-844 |
| sesame seed | fatG | 49.7 | 58 | 14-844 |
| sesame seed | carbohydrateG | 23.4 | 0.9 | 14-844 |
| dark chocolate | energyKcal | 546 | 510 | 17-491 |
| dark chocolate | proteinG | 4.9 | 5 | 17-491 |
| dark chocolate | fatG | 31.3 | 28 | 17-491 |
| dark chocolate | carbohydrateG | 61.2 | 63.5 | 17-491 |
| cocoa powder | energyKcal | 228 | 312 | 12-545 |
| cocoa powder | proteinG | 19.6 | 18.5 | 12-545 |
| cocoa powder | fatG | 13.7 | 21.7 | 12-545 |
| cocoa powder | carbohydrateG | 57.9 | 11.5 | 12-545 |
| raisin | energyKcal | 299 | 256 | 14-393 |
| raisin | proteinG | 3.1 | 3 | 14-393 |
| raisin | fatG | 0.5 | 1 | 14-393 |
| raisin | carbohydrateG | 79.2 | 62.6 | 14-393 |
| date | energyKcal | 282 | 124 | 14-083 |
| date | proteinG | 2.5 | 1.5 | 14-083 |
| date | fatG | 0.4 | 0.1 | 14-083 |
| date | carbohydrateG | 75 | 31.3 | 14-083 |
| white wine | energyKcal | 82 | 75 | 17-755 |
| white wine | carbohydrateG | 2.6 | 0.6 | 17-755 |
| red wine | energyKcal | 85 | 76 | 17-752 |
| red wine | carbohydrateG | 2.6 | 0.2 | 17-752 |

## Aliases

- **Source:** Developer-chosen everyday synonyms for keys in the staples seed: "scallion"/"scallions" for "spring onion" (common Irish/UK usage).
- **Retrieved:** 2026-09-27
- **Licence:** not stated by source
- **Reviewed:** 2026-09-28

## Seasonality

- **Source:** The Stop Food Waste Seasonal Food Calendar (`stopfoodwaste.ie`, an EPA-backed Irish initiative), a month-by-month Republic-of-Ireland produce list distinguishing fresh/in-season items from items available "from storage". The developer supplied a copy of the calendar (`docs/Print-Seasonal-Calendar-2020-2.pdf`); the implementer did not fetch it. In-season months for this seed are the months each key appears with no "from storage" flag (June–September carry no storage distinction at all in this calendar, so anything listed there is unambiguous fresh season); shoulder months the developer's copy could mark either way were left out rather than guessed. Substitutions (onion↔shallot, parsnip↔carrot) are the developer's own culinary judgement, not read from the calendar.
- **Retrieved:** 2026-09-28
- **Licence:** not stated by source
- **Reviewed:** 2026-09-28

## Section order

- **Source:** tesco.ie's online grocery department list, taken from the "Shop offers by category" row on the site's Special Offers/all-offers pages (`superdepartment=` category tiles) — the main Groceries navigation is JavaScript-rendered and not readable as plain text, so this row was used instead; it lists the same 12 top-level departments in the same order. The developer chose this order as the app's display order (a presentation convenience for a click-and-collect list, not a store-walk order); it is not a scrape of any live automated fetch.
- **Retrieved:** 2026-09-28
- **Licence:** not stated by source
- **Reviewed:** 2026-09-28
