# ZeroAgent 內建工具

這次加入 1,006 個可呼叫項目，包括 61 個離線工具與 945 項世界銀行公開統計指標。模型可以用 `tool_search` 找工具，再用 `tool_open` 啟用。

這些工具只處理當次傳入的內容，可離線執行。

- `util_text_measure` · Count characters, words, lines and UTF-8 bytes.
- `util_text_unique_lines` · Remove duplicate lines and preserve their first occurrence.
- `util_text_sort_lines` · Sort lines in Unicode order.
- `util_text_remove_blank_lines` · Remove empty or whitespace-only lines.
- `util_text_number_lines` · Add a line number to each line of supplied text.
- `util_text_extract_lines` · Return matching lines that contain an exact search string.
- `util_text_word_frequencies` · Count word occurrences and return the 50 most frequent words.
- `util_text_literal_replace` · Replace exact text, without regular expressions.
- `util_text_collapse_spacing` · Trim text and collapse repeated whitespace to single spaces.
- `util_text_normalize_unicode` · Normalize composed Unicode text to NFC for consistent comparison.
- `util_text_split_paragraphs` · Split text at blank lines into paragraphs.
- `util_markdown_headings` · Extract Markdown ATX headings outside fenced code blocks.
- `util_markdown_tasks` · List Markdown checkbox tasks and whether each one is complete.
- `util_json_pretty_print` · Validate JSON and format its indentation.
- `util_json_minify` · Validate JSON and remove formatting whitespace.
- `util_json_pointer` · Read one value at an RFC 6901 JSON Pointer.
- `util_json_describe_shape` · Summarize a JSON value's type and top-level fields.
- `util_json_compare_fields` · Compare the keys of two JSON objects and report changed values.
- `util_base64_encode` · Encode UTF-8 text as Base64.
- `util_base64_decode_text` · Decode Base64 containing valid UTF-8 text.
- `util_sha256_text` · Calculate the SHA-256 fingerprint of exact UTF-8 text.
- `util_url_components` · Inspect the parts of a URL without visiting it. User credentials are not returned.
- `util_set_compare` · Compare two lists of strings for common and unique entries.
- `util_list_duplicates` · Find repeated strings and their occurrence counts.
- `util_sum` · Add decimal numbers without binary floating-point rounding.
- `util_mean` · Calculate the arithmetic mean of numbers.
- `util_median` · Calculate the median, including even-sized lists.
- `util_min_max` · Find the minimum, maximum and range of numbers.
- `util_variance` · Calculate population variance and standard deviation.
- `util_sample_variance` · Estimate variance and standard deviation from a sample.
- `util_quartiles` · Find quartiles and the interquartile range using medians of the lower and upper halves.
- `util_numeric_modes` · Find all numbers tied for maximum frequency.
- `util_weighted_average` · Compute a weighted mean with one nonnegative weight per value.
- `util_percent_of` · Calculate an amount's percentage share.
- `util_percent_change` · Calculate percentage change from a nonzero starting value.
- `util_discount_total` · Calculate a discounted amount and the savings.
- `util_tax_total` · Calculate tax and total from an exclusive amount and supplied rate.
- `util_split_bill` · Split an amount into equal shares rounded to cents, with exact remainder handling.
- `util_round_decimal` · Round a decimal using half-even rounding to the requested places.
- `util_date_days_between` · Count signed calendar days between two dates.
- `util_date_add_days` · Add or subtract calendar days from a date.
- `util_date_add_months` · Add calendar months and clamp to the last valid day when needed.
- `util_date_week_information` · Find weekday, ISO week number and week-based year for a date.
- `util_date_business_days` · Count Monday to Friday dates, excluding the end date. Holidays are not excluded.
- `util_time_convert_zone` · Convert a timestamp with offset to an IANA time zone, accounting for daylight saving.
- `util_time_elapsed` · Calculate elapsed seconds between two timestamps with offsets.
- `util_time_to_epoch` · Convert an ISO offset timestamp to Unix seconds and milliseconds.
- `util_time_from_epoch` · Convert Unix seconds to an ISO timestamp in a chosen zone.
- `util_temperature_convert` · Convert between Celsius, Fahrenheit and Kelvin.
- `util_date_weekday_occurrences` · Count one weekday in a date range, excluding the end date.
- `util_ratio_reduce` · Reduce an integer fraction to canonical numerator and denominator.
- `util_length_convert` · length
- `util_mass_convert` · mass
- `util_area_convert` · area
- `util_volume_convert` · volume
- `util_duration_convert` · duration
- `util_data_size_convert` · data size
- `util_energy_convert` · energy
- `util_pressure_convert` · pressure
- `util_speed_convert` · speed
- `util_geo_distance` · Estimate great-circle distance between latitude and longitude pairs.

## 世界發展指標

公開統計查詢可比較最多五個國家或地區，並回傳觀測年份、更新日期及出處。每個工具固定查一個指標。缺資料時會明確顯示。查詢只傳指標代碼、地區代碼與年份給世界銀行。

資料來源為世界銀行 World Development Indicators（來源代碼 2）。部分指標由合作機構提供，使用細節請參考各指標來源頁。

| 工具名稱 | 指標 |
| --- | --- |
| `wdi_ag_con_fert_pt_zs` | Fertilizer consumption (% of fertilizer production) |
| `wdi_ag_con_fert_zs` | Fertilizer consumption (kilograms per hectare of arable land) |
| `wdi_ag_lnd_agri_k2` | Agricultural land (sq. km) |
| `wdi_ag_lnd_agri_zs` | Agricultural land (% of land area) |
| `wdi_ag_lnd_arbl_ha` | Arable land (hectares) |
| `wdi_ag_lnd_arbl_ha_pc` | Arable land (hectares per person) |
| `wdi_ag_lnd_arbl_zs` | Arable land (% of land area) |
| `wdi_ag_lnd_crel_ha` | Land under cereal production (hectares) |
| `wdi_ag_lnd_crop_zs` | Permanent cropland (% of land area) |
| `wdi_ag_lnd_el5m_ru_k2` | Rural land area where elevation is below 5 meters (sq. km) |
| `wdi_ag_lnd_el5m_ru_zs` | Rural land area where elevation is below 5 meters (% of total land area) |
| `wdi_ag_lnd_el5m_ur_k2` | Urban land area where elevation is below 5 meters (sq. km) |
| `wdi_ag_lnd_el5m_ur_zs` | Urban land area where elevation is below 5 meters (% of total land area) |
| `wdi_ag_lnd_el5m_zs` | Land area where elevation is below 5 meters (% of total land area) |
| `wdi_ag_lnd_frst_k2` | Forest area (sq. km) |
| `wdi_ag_lnd_frst_zs` | Forest area (% of land area) |
| `wdi_ag_lnd_irig_ag_zs` | Agricultural irrigated land (% of total agricultural land) |
| `wdi_ag_lnd_prcp_mm` | Average precipitation in depth (mm per year) |
| `wdi_ag_lnd_totl_k2` | Land area (sq. km) |
| `wdi_ag_lnd_totl_ru_k2` | Rural land area (sq. km) |
| `wdi_ag_lnd_totl_ur_k2` | Urban land area (sq. km) |
| `wdi_ag_prd_crel_mt` | Cereal production (metric tons) |
| `wdi_ag_prd_crop_xd` | Crop production index (2014-2016 = 100) |
| `wdi_ag_prd_food_xd` | Food production index (2014-2016 = 100) |
| `wdi_ag_prd_lvsk_xd` | Livestock production index (2014-2016 = 100) |
| `wdi_ag_srf_totl_k2` | Surface area (sq. km) |
| `wdi_ag_yld_crel_kg` | Cereal yield (kg per hectare) |
| `wdi_bg_gsr_nfsv_gd_zs` | Trade in services (% of GDP) |
| `wdi_bm_gsr_cmcp_zs` | Communications, computer, etc. (% of service imports, BoP) |
| `wdi_bm_gsr_fcty_cd` | Primary income payments (BoP, current US$) |
| `wdi_bm_gsr_gnfs_cd` | Imports of goods and services (BoP, current US$) |
| `wdi_bm_gsr_insf_zs` | Insurance and financial services (% of service imports, BoP) |
| `wdi_bm_gsr_mrch_cd` | Goods imports (BoP, current US$) |
| `wdi_bm_gsr_nfsv_cd` | Service imports (BoP, current US$) |
| `wdi_bm_gsr_royl_cd` | Charges for the use of intellectual property, payments (BoP, current US$) |
| `wdi_bm_gsr_totl_cd` | Imports of goods, services and primary income (BoP, current US$) |
| `wdi_bm_gsr_tran_zs` | Transport services (% of service imports, BoP) |
| `wdi_bm_gsr_trvl_zs` | Travel services (% of service imports, BoP) |
| `wdi_bm_klt_dinv_cd_wd` | Foreign direct investment, net outflows (BoP, current US$) |
| `wdi_bm_klt_dinv_wd_gd_zs` | Foreign direct investment, net outflows (% of GDP) |
| `wdi_bm_trf_prvt_cd` | Secondary income, other sectors, payments (BoP, current US$) |
| `wdi_bm_trf_pwkr_cd_dt` | Personal remittances, paid (current US$) |
| `wdi_bn_cab_xoka_cd` | Current account balance (BoP, current US$) |
| `wdi_bn_cab_xoka_gd_zs` | Current account balance (% of GDP) |
| `wdi_bn_fin_totl_cd` | Net financial account (BoP, current US$) |
| `wdi_bn_gsr_fcty_cd` | Net primary income (BoP, current US$) |
| `wdi_bn_gsr_gnfs_cd` | Net trade in goods and services (BoP, current US$) |
| `wdi_bn_gsr_mrch_cd` | Net trade in goods (BoP, current US$) |
| `wdi_bn_kac_eoms_cd` | Net errors and omissions (BoP, current US$) |
| `wdi_bn_klt_dinv_cd` | Foreign direct investment, net (BoP, current US$) |
| `wdi_bn_klt_ptxl_cd` | Portfolio Investment, net (BoP, current US$) |
| `wdi_bn_res_incl_cd` | Reserves and related items (BoP, current US$) |
| `wdi_bn_trf_curr_cd` | Net secondary income (BoP, current US$) |
| `wdi_bn_trf_kogt_cd` | Net capital account (BoP, current US$) |
| `wdi_bx_grt_exta_cd_wd` | Grants, excluding technical cooperation (BoP, current US$) |
| `wdi_bx_grt_tech_cd_wd` | Technical cooperation grants (BoP, current US$) |
| `wdi_bx_gsr_ccis_cd` | ICT service exports (BoP, current US$) |
| `wdi_bx_gsr_ccis_zs` | ICT service exports (% of service exports, BoP) |
| `wdi_bx_gsr_cmcp_zs` | Communications, computer, etc. (% of service exports, BoP) |
| `wdi_bx_gsr_fcty_cd` | Primary income receipts (BoP, current US$) |
| `wdi_bx_gsr_gnfs_cd` | Exports of goods and services (BoP, current US$) |
| `wdi_bx_gsr_insf_zs` | Insurance and financial services (% of service exports, BoP) |
| `wdi_bx_gsr_mrch_cd` | Goods exports (BoP, current US$) |
| `wdi_bx_gsr_nfsv_cd` | Service exports (BoP, current US$) |
| `wdi_bx_gsr_royl_cd` | Charges for the use of intellectual property, receipts (BoP, current US$) |
| `wdi_bx_gsr_totl_cd` | Exports of goods, services and primary income (BoP, current US$) |
| `wdi_bx_gsr_tran_zs` | Transport services (% of service exports, BoP) |
| `wdi_bx_gsr_trvl_zs` | Travel services (% of service exports, BoP) |
| `wdi_bx_klt_dinv_cd_wd` | Foreign direct investment, net inflows (BoP, current US$) |
| `wdi_bx_klt_dinv_wd_gd_zs` | Foreign direct investment, net inflows (% of GDP) |
| `wdi_bx_pef_totl_cd_wd` | Portfolio equity, net inflows (BoP, current US$) |
| `wdi_bx_trf_curr_cd` | Secondary income receipts (BoP, current US$) |
| `wdi_bx_trf_pwkr_cd` | Personal transfers, receipts (BoP, current US$) |
| `wdi_bx_trf_pwkr_cd_dt` | Personal remittances, received (current US$) |
| `wdi_bx_trf_pwkr_dt_gd_zs` | Personal remittances, received (% of GDP) |
| `wdi_cm_mkt_indx_zg` | S&P Global Equity Indices (annual % change) |
| `wdi_cm_mkt_lcap_cd` | Market capitalization of listed domestic companies (current US$) |
| `wdi_cm_mkt_lcap_gd_zs` | Market capitalization of listed domestic companies (% of GDP) |
| `wdi_cm_mkt_ldom_no` | Listed domestic companies, total |
| `wdi_cm_mkt_trad_cd` | Stocks traded, total value (current US$) |
| `wdi_cm_mkt_trad_gd_zs` | Stocks traded, total value (% of GDP) |
| `wdi_cm_mkt_trnr` | Stocks traded, turnover ratio of domestic shares (%) |
| `wdi_dc_dac_ausl_cd` | Net bilateral aid flows from DAC donors, Australia (current US$) |
| `wdi_dc_dac_autl_cd` | Net bilateral aid flows from DAC donors, Austria (current US$) |
| `wdi_dc_dac_bell_cd` | Net bilateral aid flows from DAC donors, Belgium (current US$) |
| `wdi_dc_dac_canl_cd` | Net bilateral aid flows from DAC donors, Canada (current US$) |
| `wdi_dc_dac_cecl_cd` | Net bilateral aid flows from DAC donors, European Union institutions (current US$) |
| `wdi_dc_dac_chel_cd` | Net bilateral aid flows from DAC donors, Switzerland (current US$) |
| `wdi_dc_dac_czel_cd` | Net bilateral aid flows from DAC donors, Czechia (current US$) |
| `wdi_dc_dac_deul_cd` | Net bilateral aid flows from DAC donors, Germany (current US$) |
| `wdi_dc_dac_dnkl_cd` | Net bilateral aid flows from DAC donors, Denmark (current US$) |
| `wdi_dc_dac_espl_cd` | Net bilateral aid flows from DAC donors, Spain (current US$) |
| `wdi_dc_dac_estl_cd` | Net bilateral aid flows from DAC donors, Estonia (current US$) |
| `wdi_dc_dac_finl_cd` | Net bilateral aid flows from DAC donors, Finland (current US$) |
| `wdi_dc_dac_fral_cd` | Net bilateral aid flows from DAC donors, France (current US$) |
| `wdi_dc_dac_gbrl_cd` | Net bilateral aid flows from DAC donors, United Kingdom (current US$) |
| `wdi_dc_dac_grcl_cd` | Net bilateral aid flows from DAC donors, Greece (current US$) |
| `wdi_dc_dac_hunl_cd` | Net bilateral aid flows from DAC donors, Hungary (current US$) |
| `wdi_dc_dac_irll_cd` | Net bilateral aid flows from DAC donors, Ireland (current US$) |
| `wdi_dc_dac_isll_cd` | Net bilateral aid flows from DAC donors, Iceland (current US$) |
| `wdi_dc_dac_ital_cd` | Net bilateral aid flows from DAC donors, Italy (current US$) |
| `wdi_dc_dac_jpnl_cd` | Net bilateral aid flows from DAC donors, Japan (current US$) |
| `wdi_dc_dac_korl_cd` | Net bilateral aid flows from DAC donors, Korea, Rep. (current US$) |
| `wdi_dc_dac_ltul_cd` | Net bilateral aid flows from DAC donors, Lithuania (current US$) |
| `wdi_dc_dac_luxl_cd` | Net bilateral aid flows from DAC donors, Luxembourg (current US$) |
| `wdi_dc_dac_nldl_cd` | Net bilateral aid flows from DAC donors, Netherlands (current US$) |
| `wdi_dc_dac_norl_cd` | Net bilateral aid flows from DAC donors, Norway (current US$) |
| `wdi_dc_dac_nzll_cd` | Net bilateral aid flows from DAC donors, New Zealand (current US$) |
| `wdi_dc_dac_poll_cd` | Net bilateral aid flows from DAC donors, Poland (current US$) |
| `wdi_dc_dac_prtl_cd` | Net bilateral aid flows from DAC donors, Portugal (current US$) |
| `wdi_dc_dac_svkl_cd` | Net bilateral aid flows from DAC donors, Slovak Republic (current US$) |
| `wdi_dc_dac_svnl_cd` | Net bilateral aid flows from DAC donors, Slovenia (current US$) |
| `wdi_dc_dac_swel_cd` | Net bilateral aid flows from DAC donors, Sweden (current US$) |
| `wdi_dc_dac_totl_cd` | Net bilateral aid flows from DAC donors, Total (current US$) |
| `wdi_dc_dac_usal_cd` | Net bilateral aid flows from DAC donors, United States (current US$) |
| `wdi_dc_oda_tldc_cd` | Net ODA provided, to the least developed countries (current US$) |
| `wdi_dc_oda_tldc_gn_zs` | Net ODA provided to the least developed countries (% of GNI) |
| `wdi_dc_oda_totl_cd` | Net ODA provided, total (current US$) |
| `wdi_dc_oda_totl_gn_zs` | Net ODA provided, total (% of GNI) |
| `wdi_dc_oda_totl_kd` | Net ODA provided, total (constant 2023 US$) |
| `wdi_dt_dod_dect_cd` | External debt stocks, total (DOD, current US$) |
| `wdi_dt_dod_dect_gn_zs` | External debt stocks (% of GNI) |
| `wdi_dt_dod_dimf_cd` | Use of IMF credit (DOD, current US$) |
| `wdi_dt_dod_dlxf_cd` | External debt stocks, long-term (DOD, current US$) |
| `wdi_dt_dod_dpng_cd` | External debt stocks, private nonguaranteed (PNG) (DOD, current US$) |
| `wdi_dt_dod_dppg_cd` | External debt stocks, public and publicly guaranteed (PPG) (DOD, current US$) |
| `wdi_dt_dod_dstc_cd` | External debt stocks, short-term (DOD, current US$) |
| `wdi_dt_dod_dstc_ir_zs` | Short-term debt (% of total reserves) |
| `wdi_dt_dod_dstc_xp_zs` | Short-term debt (% of exports of goods, services and primary income) |
| `wdi_dt_dod_dstc_zs` | Short-term debt (% of total external debt) |
| `wdi_dt_dod_mibr_cd` | PPG, IBRD (DOD, current US$) |
| `wdi_dt_dod_mida_cd` | PPG, IDA (DOD, current US$) |
| `wdi_dt_dod_mwbg_cd` | IBRD loans and IDA credits (DOD, current US$) |
| `wdi_dt_dod_pvlx_cd` | Present value of external debt (current US$) |
| `wdi_dt_dod_pvlx_ex_zs` | Present value of external debt (% of exports of goods, services and primary income) |
| `wdi_dt_dod_pvlx_gn_zs` | Present value of external debt (% of GNI) |
| `wdi_dt_nfl_blat_cd` | Net financial flows, bilateral (NFL, current US$) |
| `wdi_dt_nfl_bond_cd` | Portfolio investment, bonds (PPG + PNG) (NFL, current US$) |
| `wdi_dt_nfl_cerf_cd` | Net official flows from UN agencies, CERF (current US$) |
| `wdi_dt_nfl_dpng_cd` | Net flows on external debt, private nonguaranteed (PNG) (NFL, current US$) |
| `wdi_dt_nfl_faog_cd` | Net official flows from UN agencies, FAO (current US$) |
| `wdi_dt_nfl_iaea_cd` | Net official flows from UN agencies, IAEA (current US$) |
| `wdi_dt_nfl_ifad_cd` | Net official flows from UN agencies, IFAD (current US$) |
| `wdi_dt_nfl_ilog_cd` | Net official flows from UN agencies, ILO (current US$) |
| `wdi_dt_nfl_imfc_cd` | Net financial flows, IMF concessional (NFL, current US$) |
| `wdi_dt_nfl_imfn_cd` | Net financial flows, IMF nonconcessional (NFL, current US$) |
| `wdi_dt_nfl_mibr_cd` | Net financial flows, IBRD (NFL, current US$) |
| `wdi_dt_nfl_mida_cd` | Net financial flows, IDA (NFL, current US$) |
| `wdi_dt_nfl_mlat_cd` | Net financial flows, multilateral (NFL, current US$) |
| `wdi_dt_nfl_moth_cd` | Net financial flows, others (NFL, current US$) |
| `wdi_dt_nfl_nifc_cd` | IFC, private nonguaranteed (NFL, US$) |
| `wdi_dt_nfl_offt_cd` | PPG, official creditors (NFL, US$) |
| `wdi_dt_nfl_pbnd_cd` | PPG, bonds (NFL, current US$) |
| `wdi_dt_nfl_pcbk_cd` | PPG, commercial banks (NFL, current US$) |
| `wdi_dt_nfl_pcbo_cd` | Commercial banks and other lending (PPG + PNG) (NFL, current US$) |
| `wdi_dt_nfl_pngb_cd` | PNG, bonds (NFL, current US$) |
| `wdi_dt_nfl_pngc_cd` | PNG, commercial banks and other creditors (NFL, current US$) |
| `wdi_dt_nfl_prop_cd` | PPG, other private creditors (NFL, current US$) |
| `wdi_dt_nfl_prvt_cd` | PPG, private creditors (NFL, US$) |
| `wdi_dt_nfl_rdbc_cd` | Net financial flows, RDB concessional (NFL, current US$) |
| `wdi_dt_nfl_rdbn_cd` | Net financial flows, RDB nonconcessional (NFL, current US$) |
| `wdi_eg_cft_accs_ru_zs` | Access to clean fuels and technologies for cooking, rural (% of rural population) |
| `wdi_eg_cft_accs_ur_zs` | Access to clean fuels and technologies for cooking, urban (% of urban population) |
| `wdi_eg_cft_accs_zs` | Access to clean fuels and technologies for cooking (% of population) |
| `wdi_eg_egy_prim_pp_kd` | Energy intensity level of primary energy (MJ/$2021 PPP GDP) |
| `wdi_eg_elc_accs_ru_zs` | Access to electricity, rural (% of rural population) |
| `wdi_eg_elc_accs_ur_zs` | Access to electricity, urban (% of urban population) |
| `wdi_eg_elc_accs_zs` | Access to electricity (% of population) |
| `wdi_eg_elc_coal_zs` | Electricity production from coal sources (% of total) |
| `wdi_eg_elc_fosl_zs` | Electricity production from oil, gas and coal sources (% of total) |
| `wdi_eg_elc_hyro_zs` | Electricity production from hydroelectric sources (% of total) |
| `wdi_eg_elc_loss_zs` | Electric power transmission and distribution losses (% of output) |
| `wdi_eg_elc_ngas_zs` | Electricity production from natural gas sources (% of total) |
| `wdi_eg_elc_nucl_zs` | Electricity production from nuclear sources (% of total) |
| `wdi_eg_elc_petr_zs` | Electricity production from oil sources (% of total) |
| `wdi_eg_elc_rnew_zs` | Renewable electricity output (% of total electricity output) |
| `wdi_eg_elc_rnwx_kh` | Electricity production from renewable sources, excluding hydroelectric (kWh) |
| `wdi_eg_elc_rnwx_zs` | Electricity production from renewable sources, excluding hydroelectric (% of total) |
| `wdi_eg_fec_rnew_zs` | Renewable energy consumption (% of total final energy consumption) |
| `wdi_eg_gdp_puse_ko_pp` | GDP per unit of energy use (PPP $ per kg of oil equivalent) |
| `wdi_eg_gdp_puse_ko_pp_kd` | GDP per unit of energy use (constant 2021 PPP $ per kg of oil equivalent) |
| `wdi_eg_imp_cons_zs` | Energy imports, net (% of energy use) |
| `wdi_eg_use_comm_cl_zs` | Alternative and nuclear energy (% of total energy use) |
| `wdi_eg_use_comm_fo_zs` | Fossil fuel energy consumption (% of total) |
| `wdi_eg_use_comm_gd_pp_kd` | Energy use (kg of oil equivalent) per $1,000 GDP (constant 2021 PPP) |
| `wdi_eg_use_crnw_zs` | Combustible renewables and waste (% of total energy) |
| `wdi_eg_use_elec_kh_pc` | Electric power consumption (kWh per capita) |
| `wdi_eg_use_pcap_kg_oe` | Energy use (kg of oil equivalent per capita) |
| `wdi_en_atm_pm25_mc_m3` | PM2.5 air pollution, mean annual exposure (micrograms per cubic meter) |
| `wdi_en_atm_pm25_mc_t1_zs` | PM2.5 pollution, population exposed to levels exceeding WHO Interim Target-1 value (% of total) |
| `wdi_en_atm_pm25_mc_t2_zs` | PM2.5 pollution, population exposed to levels exceeding WHO Interim Target-2 value (% of total) |
| `wdi_en_atm_pm25_mc_t3_zs` | PM2.5 pollution, population exposed to levels exceeding WHO Interim Target-3 value (% of total) |
| `wdi_en_atm_pm25_mc_zs` | PM2.5 air pollution, population exposed to levels exceeding WHO guideline value (% of total) |
| `wdi_en_bir_thrd_no` | Bird species, threatened |
| `wdi_en_clc_drsk_xq` | Disaster risk reduction progress score (1-5 scale; 5=best) |
| `wdi_en_clc_mdat_zs` | Droughts, floods, extreme temperatures (% of population, average 1990-2009) |
| `wdi_en_fsh_thrd_no` | Fish species, threatened |
| `wdi_en_ghg_all_lu_mt_ce_ar5` | Total greenhouse gas emissions including LULUCF (Mt CO2e) |
| `wdi_en_ghg_all_mt_ce_ar5` | Total greenhouse gas emissions excluding LULUCF (Mt CO2e) |
| `wdi_en_ghg_all_pc_ce_ar5` | Total greenhouse gas emissions excluding LULUCF per capita (t CO2e/capita) |
| `wdi_en_ghg_ch4_ag_mt_ce_ar5` | Methane (CH4) emissions from Agriculture (Mt CO2e) |
| `wdi_en_ghg_ch4_bu_mt_ce_ar5` | Methane (CH4) emissions from Building (Energy) (Mt CO2e) |
| `wdi_en_ghg_ch4_fe_mt_ce_ar5` | Methane (CH4) emissions from Fugitive Emissions (Energy) (Mt CO2e) |
| `wdi_en_ghg_ch4_ic_mt_ce_ar5` | Methane (CH4) emissions from Industrial Combustion (Energy) (Mt CO2e) |
| `wdi_en_ghg_ch4_ip_mt_ce_ar5` | Methane (CH4) emissions from Industrial Processes (Mt CO2e) |
| `wdi_en_ghg_ch4_mt_ce_ar5` | Methane (CH4) emissions (total) excluding LULUCF (Mt CO2e) |
| `wdi_en_ghg_ch4_pi_mt_ce_ar5` | Methane (CH4) emissions from Power Industry (Energy) (Mt CO2e) |
| `wdi_en_ghg_ch4_tr_mt_ce_ar5` | Methane (CH4) emissions from Transport (Energy) (Mt CO2e) |
| `wdi_en_ghg_ch4_wa_mt_ce_ar5` | Methane (CH4) emissions from Waste (Mt CO2e) |
| `wdi_en_ghg_ch4_zg_ar5` | Methane (CH4) emissions (total) excluding LULUCF (% change from 1990) |
| `wdi_en_ghg_co2_ag_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Agriculture (Mt CO2e) |
| `wdi_en_ghg_co2_bu_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Building (Energy) (Mt CO2e) |
| `wdi_en_ghg_co2_fe_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Fugitive Emissions (Energy) (Mt CO2e) |
| `wdi_en_ghg_co2_ic_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Industrial Combustion (Energy) (Mt CO2e) |
| `wdi_en_ghg_co2_ip_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Industrial Processes (Mt CO2e) |
| `wdi_en_ghg_co2_lu_df_mt_ce_ar5` | Carbon dioxide (CO2) net fluxes from LULUCF - Deforestation (Mt CO2e) |
| `wdi_en_ghg_co2_lu_fl_mt_ce_ar5` | Carbon dioxide (CO2) net fluxes from LULUCF - Forest Land (Mt CO2e) |
| `wdi_en_ghg_co2_lu_mt_ce_ar5` | Carbon dioxide (CO2) net fluxes from LULUCF - Total excluding non-tropical fires (Mt CO2e) |
| `wdi_en_ghg_co2_lu_ol_mt_ce_ar5` | Carbon dioxide (CO2) net fluxes from LULUCF - Other Land (Mt CO2e) |
| `wdi_en_ghg_co2_lu_os_mt_ce_ar5` | Carbon dioxide (CO2) net fluxes from LULUCF - Organic Soil (Mt CO2e) |
| `wdi_en_ghg_co2_mt_ce_ar5` | Carbon dioxide (CO2) emissions (total) excluding LULUCF (Mt CO2e) |
| `wdi_en_ghg_co2_pc_ce_ar5` | Carbon dioxide (CO2) emissions excluding LULUCF per capita (t CO2e/capita) |
| `wdi_en_ghg_co2_pi_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Power Industry (Energy) (Mt CO2e) |
| `wdi_en_ghg_co2_rt_gdp_kd` | Carbon intensity of GDP (kg CO2e per constant 2015 US$ of GDP) |
| `wdi_en_ghg_co2_rt_gdp_pp_kd` | Carbon intensity of GDP (kg CO2e per 2021 PPP $ of GDP) |
| `wdi_en_ghg_co2_tr_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Transport (Energy) (Mt CO2e) |
| `wdi_en_ghg_co2_wa_mt_ce_ar5` | Carbon dioxide (CO2) emissions from Waste (Mt CO2e) |
| `wdi_en_ghg_co2_zg_ar5` | Carbon dioxide (CO2) emissions (total) excluding LULUCF (% change from 1990) |
| `wdi_en_ghg_fgas_ip_mt_ce_ar5` | F-gases emissions from Industrial Processes (Mt CO2e) |
| `wdi_er_fsh_aqua_mt` | Aquaculture production (metric tons) |
| `wdi_er_fsh_capt_mt` | Capture fisheries production (metric tons) |
| `wdi_er_fsh_prod_mt` | Total fisheries production (metric tons) |
| `wdi_er_gdp_fwtl_m3_kd` | Water productivity, total (constant 2015 US$ GDP per cubic meter of total freshwater withdrawal) |
| `wdi_er_h2o_fwag_zs` | Annual freshwater withdrawals, agriculture (% of total freshwater withdrawal) |
| `wdi_er_h2o_fwdm_zs` | Annual freshwater withdrawals, domestic (% of total freshwater withdrawal) |
| `wdi_er_h2o_fwin_zs` | Annual freshwater withdrawals, industry (% of total freshwater withdrawal) |
| `wdi_er_h2o_fwst_zs` | Level of water stress: freshwater withdrawal as a proportion of available freshwater resources |
| `wdi_er_h2o_fwtl_k3` | Annual freshwater withdrawals, total (billion cubic meters) |
| `wdi_er_h2o_fwtl_zs` | Annual freshwater withdrawals, total (% of internal resources) |
| `wdi_er_h2o_intr_k3` | Renewable internal freshwater resources, total (billion cubic meters) |
| `wdi_er_h2o_intr_pc` | Renewable internal freshwater resources per capita (cubic meters) |
| `wdi_er_lnd_ptld_zs` | Terrestrial protected areas (% of total land area) |
| `wdi_er_mrn_ptmr_zs` | Marine protected areas (% of territorial waters) |
| `wdi_er_ptd_totl_zs` | Terrestrial and marine protected areas (% of total territorial area) |
| `wdi_fb_ast_nper_zs` | Bank nonperforming loans to total gross loans (%) |
| `wdi_fb_atm_totl_p5` | Automated teller machines (ATMs) (per 100,000 adults) |
| `wdi_fb_bnk_capa_zs` | Bank capital to assets ratio (%) |
| `wdi_fb_cbk_brch_p5` | Commercial bank branches (per 100,000 adults) |
| `wdi_fb_cbk_brwr_p3` | Borrowers from commercial banks (per 1,000 adults) |
| `wdi_fb_cbk_dptr_p3` | Depositors with commercial banks (per 1,000 adults) |
| `wdi_fd_ast_prvt_gd_zs` | Domestic credit to private sector by banks (% of GDP) |
| `wdi_fd_res_liqu_as_zs` | Bank liquid reserves to bank assets ratio (%) |
| `wdi_fi_res_totl_cd` | Total reserves (includes gold, current US$) |
| `wdi_fi_res_totl_dt_zs` | Total reserves (% of total external debt) |
| `wdi_fi_res_totl_mo` | Total reserves in months of imports |
| `wdi_fi_res_xgld_cd` | Total reserves minus gold (current US$) |
| `wdi_fm_ast_cgov_zg_m3` | Claims on central government (annual growth as % of broad money) |
| `wdi_fm_ast_domo_zg_m3` | Claims on other sectors of the domestic economy (annual growth as % of broad money) |
| `wdi_fm_ast_doms_cn` | Net domestic credit (current LCU) |
| `wdi_fm_ast_nfrg_cn` | Net foreign assets (current LCU) |
| `wdi_fm_ast_prvt_gd_zs` | Monetary Sector credit to private sector (% GDP) |
| `wdi_fm_ast_prvt_zg_m3` | Claims on private sector (annual growth as % of broad money) |
| `wdi_fm_lbl_bmny_cn` | Broad money (current LCU) |
| `wdi_fm_lbl_bmny_gd_zs` | Broad money (% of GDP) |
| `wdi_fm_lbl_bmny_ir_zs` | Broad money to total reserves ratio |
| `wdi_fm_lbl_bmny_zg` | Broad money growth (annual %) |
| `wdi_fp_cpi_totl` | Consumer price index (2010 = 100) |
| `wdi_fp_cpi_totl_zg` | Inflation, consumer prices (annual %) |
| `wdi_fp_wpi_totl` | Wholesale price index (2010 = 100) |
| `wdi_fr_inr_dpst` | Deposit interest rate (%) |
| `wdi_fr_inr_lend` | Lending interest rate (%) |
| `wdi_fr_inr_lndp` | Interest rate spread (lending rate minus deposit rate, %) |
| `wdi_fr_inr_rinr` | Real interest rate (%) |
| `wdi_fr_inr_risk` | Risk premium on lending (lending rate minus treasury bill rate, %) |
| `wdi_fs_ast_cgov_gd_zs` | Claims on central government, etc. (% GDP) |
| `wdi_fs_ast_domo_gd_zs` | Claims on other sectors of the domestic economy (% of GDP) |
| `wdi_fs_ast_doms_gd_zs` | Domestic credit provided by financial sector (% of GDP) |
| `wdi_fs_ast_prvt_gd_zs` | Domestic credit to private sector (% of GDP) |
| `wdi_fx_own_totl_40_zs` | Account ownership at a financial institution or with a mobile-money-service provider, poorest 40% (% of population ages 15+) |
| `wdi_fx_own_totl_60_zs` | Account ownership at a financial institution or with a mobile-money-service provider, richest 60% (% of population ages 15+) |
| `wdi_fx_own_totl_fe_zs` | Account ownership at a financial institution or with a mobile-money-service provider, female (% of population ages 15+) |
| `wdi_fx_own_totl_ma_zs` | Account ownership at a financial institution or with a mobile-money-service provider, male (% of population ages 15+) |
| `wdi_fx_own_totl_ol_zs` | Account ownership at a financial institution or with a mobile-money-service provider, older adults (% of population ages 25+) |
| `wdi_fx_own_totl_pl_zs` | Account ownership at a financial institution or with a mobile-money-service provider, primary education or less (% of population ages 15+) |
| `wdi_fx_own_totl_so_zs` | Account ownership at a financial institution or with a mobile-money-service provider, secondary education or more (% of population ages 15+) |
| `wdi_fx_own_totl_yg_zs` | Account ownership at a financial institution or with a mobile-money-service provider, young adults (% of population ages 15-24) |
| `wdi_fx_own_totl_zs` | Account ownership at a financial institution or with a mobile-money-service provider (% of population ages 15+) |
| `wdi_gb_xpd_rsdv_gd_zs` | Research and development expenditure (% of GDP) |
| `wdi_gc_ast_totl_cn` | Net acquisition of financial assets (current LCU) |
| `wdi_gc_ast_totl_gd_zs` | Net acquisition of financial assets (% of GDP) |
| `wdi_gc_dod_totl_cn` | Central government debt, total (current LCU) |
| `wdi_gc_dod_totl_gd_zs` | Central government debt, total (% of GDP) |
| `wdi_gc_lbl_totl_cn` | Net incurrence of liabilities, total (current LCU) |
| `wdi_gc_lbl_totl_gd_zs` | Net incurrence of liabilities, total (% of GDP) |
| `wdi_gc_nfn_totl_cn` | Net investment in nonfinancial assets (current LCU) |
| `wdi_gc_nfn_totl_gd_zs` | Net investment in nonfinancial assets (% of GDP) |
| `wdi_gc_nld_totl_cn` | Net lending (+) / net borrowing (-) (current LCU) |
| `wdi_gc_nld_totl_gd_zs` | Net lending (+) / net borrowing (-) (% of GDP) |
| `wdi_gc_rev_gotr_cn` | Grants and other revenue (current LCU) |
| `wdi_gc_rev_gotr_zs` | Grants and other revenue (% of revenue) |
| `wdi_gc_rev_socl_cn` | Social contributions (current LCU) |
| `wdi_gc_rev_socl_zs` | Social contributions (% of revenue) |
| `wdi_gc_rev_xgrt_cn` | Revenue, excluding grants (current LCU) |
| `wdi_gc_rev_xgrt_gd_zs` | Revenue, excluding grants (% of GDP) |
| `wdi_gc_tax_expt_cn` | Taxes on exports (current LCU) |
| `wdi_gc_tax_expt_zs` | Taxes on exports (% of tax revenue) |
| `wdi_gc_tax_gsrv_cn` | Taxes on goods and services (current LCU) |
| `wdi_gc_tax_gsrv_rv_zs` | Taxes on goods and services (% of revenue) |
| `wdi_gc_tax_gsrv_va_zs` | Taxes on goods and services (% value added of industry and services) |
| `wdi_gc_tax_impt_cn` | Customs and other import duties (current LCU) |
| `wdi_gc_tax_impt_zs` | Customs and other import duties (% of tax revenue) |
| `wdi_gc_tax_intt_cn` | Taxes on international trade (current LCU) |
| `wdi_gc_tax_intt_rv_zs` | Taxes on international trade (% of revenue) |
| `wdi_gc_tax_othr_cn` | Other taxes (current LCU) |
| `wdi_gc_tax_othr_rv_zs` | Other taxes (% of revenue) |
| `wdi_gc_tax_totl_cn` | Tax revenue (current LCU) |
| `wdi_gc_tax_totl_gd_zs` | Tax revenue (% of GDP) |
| `wdi_gc_tax_ypkg_cn` | Taxes on income, profits and capital gains (current LCU) |
| `wdi_gc_tax_ypkg_rv_zs` | Taxes on income, profits and capital gains (% of revenue) |
| `wdi_gc_tax_ypkg_zs` | Taxes on income, profits and capital gains (% of total taxes) |
| `wdi_gc_xpn_comp_cn` | Compensation of employees (current LCU) |
| `wdi_gc_xpn_comp_zs` | Compensation of employees (% of expense) |
| `wdi_gc_xpn_gsrv_cn` | Goods and services expense (current LCU) |
| `wdi_gc_xpn_gsrv_zs` | Goods and services expense (% of expense) |
| `wdi_gc_xpn_intp_rv_zs` | Interest payments (% of revenue) |
| `wdi_gc_xpn_intp_zs` | Interest payments (% of expense) |
| `wdi_gc_xpn_othr_zs` | Other expense (% of expense) |
| `wdi_gc_xpn_totl_gd_zs` | Expense (% of GDP) |
| `wdi_gc_xpn_trft_zs` | Subsidies and other transfers (% of expense) |
| `wdi_gd_wbl_ovl_enf` | WBL: Enforcement Perceptions Index (scale 0-100) |
| `wdi_gd_wbl_ovl_law` | WBL: Legal Framework Index (scale 0-100) |
| `wdi_gd_wbl_ovl_sfr` | WBL: Supportive Framework Index (scale 0-100) |
| `wdi_gf_xpd_budg_zs` | Primary government expenditures as a proportion of original approved budget (%) |
| `wdi_gov_wgi_cc_est` | Control of Corruption - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_cc_sc` | Control of Corruption - Governance score (0-100) |
| `wdi_gov_wgi_ge_est` | Government Effectiveness - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_ge_sc` | Government Effectiveness - Governance score (0-100) |
| `wdi_gov_wgi_pv_est` | Political Stability - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_pv_sc` | Political Stability - Governance score (0-100) |
| `wdi_gov_wgi_rl_est` | Rule of Law - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_rl_sc` | Rule of Law - Governance score (0-100) |
| `wdi_gov_wgi_rq_est` | Regulatory Quality - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_rq_sc` | Regulatory Quality - Governance score (0-100) |
| `wdi_gov_wgi_va_est` | Voice and Accountability - Governance estimate (approx. -2.5 to +2.5) |
| `wdi_gov_wgi_va_sc` | Voice and Accountability - Governance score (0-100) |
| `wdi_hd_hcip_educ_fe` | Human capital index plus (HCI+): education pillar score, female (scale 0–188) |
| `wdi_hd_hcip_educ_ma` | Human capital index plus (HCI+): education pillar score, male (scale 0–188) |
| `wdi_hd_hcip_educ_to` | Human capital index plus (HCI+): education pillar score, total (scale 0–188) |
| `wdi_hd_hcip_hlth_fe` | Human capital index plus (HCI+): health pillar score, female (scale 0–50) |
| `wdi_hd_hcip_hlth_ma` | Human capital index plus (HCI+): health pillar score, male (scale 0–50) |
| `wdi_hd_hcip_hlth_to` | Human capital index plus (HCI+): health pillar score, total (scale 0–50) |
| `wdi_hd_hcip_otjl_fe` | Human capital index plus (HCI+): on-the-job learning pillar score, female (scale -30–87) |
| `wdi_hd_hcip_otjl_ma` | Human capital index plus (HCI+): on-the-job learning pillar score, male (scale -30–87) |
| `wdi_hd_hcip_otjl_to` | Human capital index plus (HCI+): on-the-job learning pillar score, total (scale -30–87) |
| `wdi_hd_hcip_ovrl_fe` | Human capital index plus (HCI+): overall score, female (scale 0–325) |
| `wdi_hd_hcip_ovrl_ma` | Human capital index plus (HCI+): overall score, male (scale 0–325) |
| `wdi_hd_hcip_ovrl_to` | Human capital index plus (HCI+): overall score, total (scale 0–325) |
| `wdi_ic_bre_be_os` | B-READY: Business Entry: Overall Score |
| `wdi_ic_bre_be_p1` | B-READY: Business Entry Pillar 1: Quality of Regulations for Business Entry |
| `wdi_ic_bre_be_p2` | B-READY: Business Entry Pillar 2: Digital Public Services and Transparency of Information for Business Entry |
| `wdi_ic_bre_be_p3` | B-READY: Business Entry Pillar 3: Operational Efficiency of Business Entry |
| `wdi_ic_bre_bi_os` | B-READY: Business Insolvency: Overall Score |
| `wdi_ic_bre_bi_p1` | B-READY: Business Insolvency Pillar 1: Quality of Regulations for Judicial Insolvency Proceedings |
| `wdi_ic_bre_bi_p2` | B-READY: Business Insolvency Pillar 2: Quality of Institutional and Operational Infrastructure for Judicial Insolvency Proceedings |
| `wdi_ic_bre_bi_p3` | B-READY: Business Insolvency Pillar 3: Operational Efficiency of Resolving Judicial Insolvency Proceedings |
| `wdi_ic_bre_bl_os` | B-READY: Business Location: Overall Score |
| `wdi_ic_bre_bl_p1` | B-READY: Business Location Pillar 1: Quality of Regulations for Business Location |
| `wdi_ic_bre_bl_p2` | B-READY: Business Location Pillar 2: Quality of Public Services and Transparency of Information for Business Location |
| `wdi_ic_bre_bl_p3` | B-READY: Business Location Pillar 3: Operational Efficiency of Establishing a Business Location |
| `wdi_ic_bre_dr_os` | B-READY: Dispute Resolution: Overall Score |
| `wdi_ic_bre_dr_p1` | B-READY: Dispute Resolution Pillar 1: Quality of Regulations for Dispute Resolution |
| `wdi_ic_bre_dr_p2` | B-READY: Dispute Resolution Pillar 2: Public Services for Dispute Resolution |
| `wdi_ic_bre_dr_p3` | B-READY: Dispute Resolution Pillar 3: Ease of Resolving a Commercial Dispute |
| `wdi_ic_bre_fs_os` | B-READY: Financial Services: Overall Score |
| `wdi_ic_bre_fs_p1` | B-READY: Financial Services Pillar 1: Quality of Regulations for Financial Services |
| `wdi_ic_bre_fs_p2` | B-READY: Financial Services Pillar 2: Accessibility of Information in Credit Infrastructure |
| `wdi_ic_bre_fs_p3` | B-READY: Financial Services Pillar 3: Operational Efficiency of Receiving Financial Services |
| `wdi_ic_bre_it_os` | B-READY: International Trade: Overall Score |
| `wdi_ic_bre_it_p1` | B-READY: International Trade Pillar 1: Quality of Regulations for International Trade |
| `wdi_ic_bre_it_p2` | B-READY: International Trade Pillar 2: Quality of Public Services for the Facilitation of International Trade |
| `wdi_ic_bre_it_p3` | B-READY: International Trade Pillar 3: Efficiency of Importing Goods, Exporting Goods, and Engaging in Digital Trade |
| `wdi_ic_bre_lb_os` | B-READY: Labor: Overall Score |
| `wdi_ic_bre_lb_p1` | B-READY: Labor Pillar 1: Quality of Labor Regulations |
| `wdi_ic_bre_lb_p2` | B-READY: Labor Pillar 2: Adequacy of Public Services for Labor |
| `wdi_ic_bre_lb_p3` | B-READY: Labor Pillar 3: Operational Efficiency of Labor Regulations and Public Services in Practice |
| `wdi_ic_bre_mc_os` | B-READY: Market Competition: Overall Score |
| `wdi_ic_bre_mc_p1` | B-READY: Market Competition Pillar 1: Quality of Regulations that Promote Market Competition |
| `wdi_ic_bre_mc_p2` | B-READY: Market Competition Pillar 2: Public Services that Promote Market Competition |
| `wdi_ic_bre_mc_p3` | B-READY: Market Competition Pillar 3: Implementation of Key Services Promoting Market Competition |
| `wdi_ic_bre_p1_rf` | B-READY: Pillar 1: Regulatory Framework |
| `wdi_ic_bre_p2_ps` | B-READY: Pillar 2: Public Services |
| `wdi_ic_bre_p3_oe` | B-READY: Pillar 3: Operational Efficiency |
| `wdi_ic_bre_tx_os` | B-READY: Taxation: Overall Score |
| `wdi_ic_bre_tx_p1` | B-READY: Taxation Pillar 1: Quality of Regulations on Taxation |
| `wdi_ic_bre_tx_p2` | B-READY: Taxation Pillar 2: Public Services Provided by the Tax Administration |
| `wdi_ic_bre_tx_p3` | B-READY: Taxation Pillar 3: Operational Efficiency of Tax Systems in Practice |
| `wdi_ic_bre_us_os` | B-READY: Utility Services: Overall Score |
| `wdi_ic_bre_us_p1` | B-READY: Utility Services Pillar 1: Quality of Regulations on Utility Services |
| `wdi_ie_ppi_engy_cd` | Investment in energy with private participation (current US$) |
| `wdi_ie_ppi_icti_cd` | Investment in ICT with private participation (current US$) |
| `wdi_ie_ppi_tran_cd` | Investment in transport with private participation (current US$) |
| `wdi_ie_ppi_watr_cd` | Investment in water and sanitation with private participation (current US$) |
| `wdi_ie_ppn_engy_cd` | Public private partnerships investment in energy (current US$) |
| `wdi_ie_ppn_icti_cd` | Public private partnerships investment in ICT (current US$) |
| `wdi_ie_ppn_tran_cd` | Public private partnerships investment in transport (current US$) |
| `wdi_ie_ppn_watr_cd` | Public private partnerships investment in water and sanitation (current US$) |
| `wdi_ip_ids_nrct` | Industrial design applications, nonresident, by count |
| `wdi_ip_ids_rsct` | Industrial design applications, resident, by count |
| `wdi_ip_jrn_artc_sc` | Scientific and technical journal articles |
| `wdi_ip_pat_nres` | Patent applications, nonresidents |
| `wdi_ip_pat_resd` | Patent applications, residents |
| `wdi_ip_tmk_nrct` | Trademark applications, nonresident, by count |
| `wdi_ip_tmk_rsct` | Trademark applications, resident, by count |
| `wdi_iq_cpa_breg_xq` | CPIA business regulatory environment rating (1=low to 6=high) |
| `wdi_iq_cpa_debt_xq` | CPIA debt policy rating (1=low to 6=high) |
| `wdi_iq_cpa_econ_xq` | CPIA economic management cluster average (1=low to 6=high) |
| `wdi_iq_cpa_envr_xq` | CPIA policy and institutions for environmental sustainability rating (1=low to 6=high) |
| `wdi_iq_cpa_finq_xq` | CPIA quality of budgetary and financial management rating (1=low to 6=high) |
| `wdi_iq_cpa_fins_xq` | CPIA financial sector rating (1=low to 6=high) |
| `wdi_iq_cpa_fisp_xq` | CPIA fiscal policy rating (1=low to 6=high) |
| `wdi_iq_cpa_gndr_xq` | CPIA gender equality rating (1=low to 6=high) |
| `wdi_iq_cpa_hres_xq` | CPIA building human resources rating (1=low to 6=high) |
| `wdi_iq_cpa_irai_xq` | IDA resource allocation index (1=low to 6=high) |
| `wdi_iq_cpa_macr_xq` | CPIA macroeconomic management rating (1=low to 6=high) |
| `wdi_iq_cpa_padm_xq` | CPIA quality of public administration rating (1=low to 6=high) |
| `wdi_iq_cpa_pres_xq` | CPIA equity of public resource use rating (1=low to 6=high) |
| `wdi_iq_cpa_prop_xq` | CPIA property rights and rule-based governance rating (1=low to 6=high) |
| `wdi_iq_cpa_prot_xq` | CPIA social protection rating (1=low to 6=high) |
| `wdi_iq_cpa_pubs_xq` | CPIA public sector management and institutions cluster average (1=low to 6=high) |
| `wdi_iq_cpa_revn_xq` | CPIA efficiency of revenue mobilization rating (1=low to 6=high) |
| `wdi_iq_cpa_soci_xq` | CPIA policies for social inclusion/equity cluster average (1=low to 6=high) |
| `wdi_iq_cpa_strc_xq` | CPIA structural policies cluster average (1=low to 6=high) |
| `wdi_iq_cpa_trad_xq` | CPIA trade rating (1=low to 6=high) |
| `wdi_iq_cpa_tran_xq` | CPIA transparency, accountability, and corruption in the public sector rating (1=low to 6=high) |
| `wdi_iq_spi_ovrl` | Statistical performance indicators (SPI): Overall score (scale 0-100) |
| `wdi_iq_spi_pil1` | Statistical performance indicators (SPI): Pillar 1 data use score (scale 0-100) |
| `wdi_iq_spi_pil2` | Statistical performance indicators (SPI): Pillar 2 data services score (scale 0-100) |
| `wdi_iq_spi_pil3` | Statistical performance indicators (SPI): Pillar 3 data products score (scale 0-100) |
| `wdi_iq_spi_pil4` | Statistical performance indicators (SPI): Pillar 4 data sources score (scale 0-100) |
| `wdi_iq_spi_pil5` | Statistical performance indicators (SPI): Pillar 5 data infrastructure score (scale 0-100) |
| `wdi_is_air_dprt` | Air transport, registered carrier departures worldwide |
| `wdi_is_air_good_mt_k1` | Air transport, freight (million ton-km) |
| `wdi_is_air_psgr` | Air transport, passengers carried |
| `wdi_is_rrs_good_mt_k6` | Railways, goods transported (million ton-km) |
| `wdi_is_rrs_pasg_km` | Railways, passengers carried (million passenger-km) |
| `wdi_is_rrs_totl_km` | Rail lines (total route-km) |
| `wdi_is_shp_gcnw_xq` | Liner shipping connectivity index (maximum value in 2004 = 100) |
| `wdi_is_shp_good_tu` | Container port traffic (TEU: 20 foot equivalent units) |
| `wdi_it_cel_sets` | Mobile cellular subscriptions |
| `wdi_it_cel_sets_p2` | Mobile cellular subscriptions (per 100 people) |
| `wdi_it_mlt_main` | Fixed telephone subscriptions |
| `wdi_it_mlt_main_p2` | Fixed telephone subscriptions (per 100 people) |
| `wdi_it_net_bbnd` | Fixed broadband subscriptions |
| `wdi_it_net_bbnd_p2` | Fixed broadband subscriptions (per 100 people) |
| `wdi_it_net_secr` | Secure Internet servers |
| `wdi_it_net_secr_p6` | Secure Internet servers (per 1 million people) |
| `wdi_it_net_user_fe_zs` | Individuals using the Internet, female (% of female population) |
| `wdi_it_net_user_ma_zs` | Individuals using the Internet, male (% of male population) |
| `wdi_it_net_user_zs` | Individuals using the Internet (% of population) |
| `wdi_lp_exp_durs_md` | Lead time to export, median case (days) |
| `wdi_lp_imp_durs_md` | Lead time to import, median case (days) |
| `wdi_lp_lpi_cust_xq` | Logistics performance index: Efficiency of customs clearance process (1=low to 5=high) |
| `wdi_lp_lpi_infr_xq` | Logistics performance index: Quality of trade and transport-related infrastructure (1=low to 5=high) |
| `wdi_lp_lpi_itrn_xq` | Logistics performance index: Ease of arranging competitively priced shipments (1=low to 5=high) |
| `wdi_lp_lpi_logs_xq` | Logistics performance index: Competence and quality of logistics services (1=low to 5=high) |
| `wdi_lp_lpi_ovrl_xq` | Logistics performance index: Overall (1=low to 5=high) |
| `wdi_lp_lpi_time_xq` | Logistics performance index: Frequency with which shipments reach consignee within scheduled or expected time (1=low to 5=high) |
| `wdi_lp_lpi_trac_xq` | Logistics performance index: Ability to track and trace consignments (1=low to 5=high) |
| `wdi_ms_mil_mprt_kd` | Arms imports (SIPRI trend indicator values) |
| `wdi_ms_mil_totl_p1` | Armed forces personnel, total |
| `wdi_ms_mil_totl_tf_zs` | Armed forces personnel (% of total labor force) |
| `wdi_ms_mil_xpnd_cd` | Military expenditure (current USD) |
| `wdi_ms_mil_xpnd_cn` | Military expenditure (current LCU) |
| `wdi_ms_mil_xpnd_gd_zs` | Military expenditure (% of GDP) |
| `wdi_ms_mil_xpnd_zs` | Military expenditure (% of general government expenditure) |
| `wdi_ms_mil_xprt_kd` | Arms exports (SIPRI trend indicator values) |
| `wdi_ne_con_govt_cd` | General government final consumption expenditure (current US$) |
| `wdi_ne_con_govt_kd` | General government final consumption expenditure (constant 2015 US$) |
| `wdi_ne_con_govt_kd_zg` | General government final consumption expenditure (annual % growth) |
| `wdi_ne_con_govt_zs` | General government final consumption expenditure (% of GDP) |
| `wdi_ne_con_pcap_pp_cd` | Households and NPISHs Final consumption expenditure per capita, PPP (current international $) |
| `wdi_ne_con_pcap_pp_kd` | Households and NPISHs Final consumption expenditure per capita, PPP (constant 2021 international $) |
| `wdi_ne_con_prvt_cd` | Households and NPISHs Final consumption expenditure (current US$) |
| `wdi_ne_con_prvt_kd` | Households and NPISHs Final consumption expenditure (constant 2015 US$) |
| `wdi_ne_con_prvt_kd_zg` | Households and NPISHs Final consumption expenditure (annual % growth) |
| `wdi_ne_con_prvt_pc_kd` | Households and NPISHs Final consumption expenditure per capita (constant 2015 US$) |
| `wdi_ne_con_prvt_pc_kd_zg` | Households and NPISHs Final consumption expenditure per capita growth (annual %) |
| `wdi_ne_con_prvt_pp_cd` | Households and NPISHs Final consumption expenditure, PPP (current international $) |
| `wdi_ne_con_prvt_pp_kd` | Households and NPISHs Final consumption expenditure, PPP (constant 2021 international $) |
| `wdi_ne_con_prvt_zs` | Households and NPISHs final consumption expenditure (% of GDP) |
| `wdi_ne_con_totl_cd` | Final consumption expenditure (current US$) |
| `wdi_ne_con_totl_kd` | Final consumption expenditure (constant 2015 US$) |
| `wdi_ne_con_totl_kd_zg` | Final consumption expenditure (annual % growth) |
| `wdi_ne_con_totl_zs` | Final consumption expenditure (% of GDP) |
| `wdi_ne_dab_defl_zs` | Gross national expenditure deflator (base year varies by country) |
| `wdi_ne_dab_totl_cd` | Gross national expenditure (current US$) |
| `wdi_ne_dab_totl_kd` | Gross national expenditure (constant 2015 US$) |
| `wdi_ne_dab_totl_zs` | Gross national expenditure (% of GDP) |
| `wdi_ne_exp_gnfs_cd` | Exports of goods and services (current US$) |
| `wdi_ne_exp_gnfs_kd` | Exports of goods and services (constant 2015 US$) |
| `wdi_ne_exp_gnfs_kd_zg` | Exports of goods and services (annual % growth) |
| `wdi_ne_exp_gnfs_zs` | Exports of goods and services (% of GDP) |
| `wdi_ne_gdi_fprv_zs` | Gross fixed capital formation, private sector (% of GDP) |
| `wdi_ne_gdi_ftot_cd` | Gross fixed capital formation (current US$) |
| `wdi_ne_gdi_ftot_kd` | Gross fixed capital formation (constant 2015 US$) |
| `wdi_ne_gdi_ftot_kd_zg` | Gross fixed capital formation (annual % growth) |
| `wdi_ne_gdi_ftot_zs` | Gross fixed capital formation (% of GDP) |
| `wdi_ne_gdi_stkb_cd` | Changes in inventories (current US$) |
| `wdi_ne_gdi_totl_cd` | Gross capital formation (current US$) |
| `wdi_ne_gdi_totl_kd` | Gross capital formation (constant 2015 US$) |
| `wdi_ne_gdi_totl_kd_zg` | Gross capital formation (annual % growth) |
| `wdi_ne_gdi_totl_zs` | Gross capital formation (% of GDP) |
| `wdi_ne_imp_gnfs_cd` | Imports of goods and services (current US$) |
| `wdi_ne_imp_gnfs_kd` | Imports of goods and services (constant 2015 US$) |
| `wdi_ne_imp_gnfs_kd_zg` | Imports of goods and services (annual % growth) |
| `wdi_ne_imp_gnfs_zs` | Imports of goods and services (% of GDP) |
| `wdi_ne_trd_gnfs_zs` | Trade (% of GDP) |
| `wdi_nv_agr_empl_kd` | Agriculture, forestry, and fishing, value added per worker (constant 2015 US$) |
| `wdi_nv_agr_totl_cd` | Agriculture, forestry, and fishing, value added (current US$) |
| `wdi_nv_agr_totl_cn` | Agriculture, forestry, and fishing, value added (current LCU) |
| `wdi_nv_agr_totl_kd` | Agriculture, forestry, and fishing, value added (constant 2015 US$) |
| `wdi_nv_agr_totl_kd_zg` | Agriculture, forestry, and fishing, value added (annual % growth) |
| `wdi_nv_agr_totl_kn` | Agriculture, forestry, and fishing, value added (constant LCU) |
| `wdi_nv_agr_totl_zs` | Agriculture, forestry, and fishing, value added (% of GDP) |
| `wdi_nv_fsm_totl_cn` | Financial intermediary services indirectly Measured (FISIM) (current LCU) |
| `wdi_nv_fsm_totl_kn` | Financial intermediary services indirectly Measured (FISIM) (constant LCU) |
| `wdi_nv_ind_empl_kd` | Industry (including construction), value added per worker (constant 2015 US$) |
| `wdi_nv_ind_manf_cd` | Manufacturing, value added (current US$) |
| `wdi_nv_ind_manf_cn` | Manufacturing, value added (current LCU) |
| `wdi_nv_ind_manf_kd` | Manufacturing, value added (constant 2015 US$) |
| `wdi_nv_ind_manf_kd_zg` | Manufacturing, value added (annual % growth) |
| `wdi_nv_ind_manf_kn` | Manufacturing, value added (constant LCU) |
| `wdi_nv_ind_manf_zs` | Manufacturing, value added (% of GDP) |
| `wdi_nv_ind_totl_cd` | Industry (including construction), value added (current US$) |
| `wdi_nv_ind_totl_cn` | Industry (including construction), value added (current LCU) |
| `wdi_nv_ind_totl_kd` | Industry (including construction), value added (constant 2015 US$) |
| `wdi_nv_ind_totl_kd_zg` | Industry (including construction), value added (annual % growth) |
| `wdi_nv_ind_totl_kn` | Industry (including construction), value added (constant LCU) |
| `wdi_nv_ind_totl_zs` | Industry (including construction), value added (% of GDP) |
| `wdi_nv_mnf_chem_zs_un` | Chemicals (% of value added in manufacturing) |
| `wdi_nv_mnf_fbto_zs_un` | Food, beverages and tobacco (% of value added in manufacturing) |
| `wdi_nv_mnf_mtrn_zs_un` | Machinery and transport equipment (% of value added in manufacturing) |
| `wdi_nv_mnf_othr_zs_un` | Other manufacturing (% of value added in manufacturing) |
| `wdi_nv_mnf_tech_zs_un` | Medium and high-tech manufacturing value added (% manufacturing value added) |
| `wdi_nv_mnf_txtl_zs_un` | Textiles and clothing (% of value added in manufacturing) |
| `wdi_nv_srv_empl_kd` | Services, value added per worker (constant 2015 US$) |
| `wdi_nv_srv_totl_cd` | Services, value added (current US$) |
| `wdi_nv_srv_totl_cn` | Services, value added (current LCU) |
| `wdi_nv_srv_totl_kd` | Services, value added (constant 2015 US$) |
| `wdi_nv_srv_totl_kd_zg` | Services, value added (annual % growth) |
| `wdi_nv_srv_totl_kn` | Services, value added (constant LCU) |
| `wdi_nv_srv_totl_zs` | Services, value added (% of GDP) |
| `wdi_ny_adj_aedu_cd` | Adjusted savings: education expenditure (current US$) |
| `wdi_ny_adj_aedu_gn_zs` | Adjusted savings: education expenditure (% of GNI) |
| `wdi_ny_adj_dco2_cd` | Adjusted savings: carbon dioxide damage (current US$) |
| `wdi_ny_adj_dco2_gn_zs` | Adjusted savings: carbon dioxide damage (% of GNI) |
| `wdi_ny_adj_dfor_cd` | Adjusted savings: net forest depletion (current US$) |
| `wdi_ny_adj_dfor_gn_zs` | Adjusted savings: net forest depletion (% of GNI) |
| `wdi_ny_adj_dkap_cd` | Adjusted savings: consumption of fixed capital (current US$) |
| `wdi_ny_adj_dkap_gn_zs` | Adjusted savings: consumption of fixed capital (% of GNI) |
| `wdi_ny_adj_dmin_cd` | Adjusted savings: mineral depletion (current US$) |
| `wdi_ny_adj_dmin_gn_zs` | Adjusted savings: mineral depletion (% of GNI) |
| `wdi_ny_adj_dngy_cd` | Adjusted savings: energy depletion (current US$) |
| `wdi_ny_adj_dngy_gn_zs` | Adjusted savings: energy depletion (% of GNI) |
| `wdi_ny_adj_dpem_cd` | Adjusted savings: particulate emission damage (current US$) |
| `wdi_ny_adj_dpem_gn_zs` | Adjusted savings: particulate emission damage (% of GNI) |
| `wdi_ny_adj_dres_gn_zs` | Adjusted savings: natural resources depletion (% of GNI) |
| `wdi_ny_adj_ictr_gn_zs` | Adjusted savings: gross savings (% of GNI) |
| `wdi_ny_adj_nnat_cd` | Adjusted savings: net national savings (current US$) |
| `wdi_ny_adj_nnat_gn_zs` | Adjusted savings: net national savings (% of GNI) |
| `wdi_ny_adj_nnty_cd` | Adjusted net national income (current US$) |
| `wdi_ny_adj_nnty_kd` | Adjusted net national income (constant 2015 US$) |
| `wdi_ny_adj_nnty_kd_zg` | Adjusted net national income (annual % growth) |
| `wdi_ny_adj_nnty_pc_cd` | Adjusted net national income per capita (current US$) |
| `wdi_ny_adj_nnty_pc_kd` | Adjusted net national income per capita (constant 2015 US$) |
| `wdi_ny_adj_nnty_pc_kd_zg` | Adjusted net national income per capita (annual % growth) |
| `wdi_ny_adj_svng_cd` | Adjusted net savings, including particulate emission damage (current US$) |
| `wdi_ny_adj_svng_gn_zs` | Adjusted net savings, including particulate emission damage (% of GNI) |
| `wdi_ny_adj_svnx_cd` | Adjusted net savings, excluding particulate emission damage (current US$) |
| `wdi_ny_adj_svnx_gn_zs` | Adjusted net savings, excluding particulate emission damage (% of GNI) |
| `wdi_ny_gdp_coal_rt_zs` | Coal rents (% of GDP) |
| `wdi_ny_gdp_defl_kd_zg` | Inflation, GDP deflator (annual %) |
| `wdi_ny_gdp_defl_kd_zg_ad` | Inflation, GDP deflator: linked series (annual %) |
| `wdi_ny_gdp_defl_zs` | GDP deflator (base year varies by country) |
| `wdi_ny_gdp_defl_zs_ad` | GDP deflator: linked series (base year varies by country) |
| `wdi_ny_gdp_fcst_cd` | Gross value added at basic prices (GVA) (current US$) |
| `wdi_ny_gdp_fcst_kd` | Gross value added at basic prices (GVA) (constant 2015 US$) |
| `wdi_ny_gdp_frst_rt_zs` | Forest rents (% of GDP) |
| `wdi_ny_gdp_minr_rt_zs` | Mineral rents (% of GDP) |
| `wdi_ny_gdp_mktp_cd` | GDP (current US$) |
| `wdi_ny_gdp_mktp_kd` | GDP (constant 2015 US$) |
| `wdi_ny_gdp_mktp_kd_zg` | GDP growth (annual %) |
| `wdi_ny_gdp_pcap_cd` | GDP per capita (current US$) |
| `wdi_pa_nus_atls` | DEC alternative conversion factor (LCU per US$) |
| `wdi_pa_nus_fcrf` | Official exchange rate (LCU per US$, period average) |
| `wdi_pa_nus_gdp_pli` | Price level index (GDP) |
| `wdi_pa_nus_ppp` | PPP conversion factor, GDP (LCU per international $) |
| `wdi_pa_nus_prvt_pli` | Price level index (Households and NPISHs Final consumption expenditure) |
| `wdi_pa_nus_prvt_pp` | PPP conversion factor, households and NPISHs Final consumption expenditure (LCU per international $) |
| `wdi_per_allsp_adq_pop_tot` | Adequacy of social protection and labor programs (% of total welfare of beneficiary households) |
| `wdi_per_allsp_ben_q1_tot` | Benefit incidence of social protection and labor programs to poorest quintile (% of total SPL benefits) |
| `wdi_per_allsp_cov_pop_tot` | Coverage of social protection and labor programs (% of population) |
| `wdi_per_lm_alllm_adq_pop_tot` | Adequacy of unemployment benefits and ALMP (% of total welfare of beneficiary households) |
| `wdi_per_lm_alllm_ben_q1_tot` | Benefit incidence of unemployment benefits and ALMP to poorest quintile (% of total U/ALMP benefits) |
| `wdi_per_lm_alllm_cov_pop_tot` | Coverage of unemployment benefits and ALMP (% of population) |
| `wdi_per_lm_alllm_cov_q1_tot` | Coverage of unemployment benefits and ALMP in poorest quintile (% of population) |
| `wdi_per_lm_alllm_cov_q2_tot` | Coverage of unemployment benefits and ALMP in 2nd quintile (% of population) |
| `wdi_per_lm_alllm_cov_q3_tot` | Coverage of unemployment benefits and ALMP in 3rd quintile (% of population) |
| `wdi_per_lm_alllm_cov_q4_tot` | Coverage of unemployment benefits and ALMP in 4th quintile (% of population) |
| `wdi_per_lm_alllm_cov_q5_tot` | Coverage of unemployment benefits and ALMP in richest quintile (% of population) |
| `wdi_per_sa_allsa_adq_pop_tot` | Adequacy of social safety net programs (% of total welfare of beneficiary households) |
| `wdi_per_sa_allsa_ben_q1_tot` | Benefit incidence of social safety net programs to poorest quintile (% of total safety net benefits) |
| `wdi_per_sa_allsa_cov_pop_tot` | Coverage of social safety net programs (% of population) |
| `wdi_per_sa_allsa_cov_q1_tot` | Coverage of social safety net programs in poorest quintile (% of population) |
| `wdi_per_sa_allsa_cov_q2_tot` | Coverage of social safety net programs in 2nd quintile (% of population) |
| `wdi_per_sa_allsa_cov_q3_tot` | Coverage of social safety net programs in 3rd quintile (% of population) |
| `wdi_per_sa_allsa_cov_q4_tot` | Coverage of social safety net programs in 4th quintile (% of population) |
| `wdi_per_sa_allsa_cov_q5_tot` | Coverage of social safety net programs in richest quintile (% of population) |
| `wdi_per_si_allsi_adq_pop_tot` | Adequacy of social insurance programs (% of total welfare of beneficiary households) |
| `wdi_per_si_allsi_ben_q1_tot` | Benefit incidence of social insurance programs to poorest quintile (% of total social insurance benefits) |
| `wdi_per_si_allsi_cov_pop_tot` | Coverage of social insurance programs (% of population) |
| `wdi_per_si_allsi_cov_q1_tot` | Coverage of social insurance programs in poorest quintile (% of population) |
| `wdi_per_si_allsi_cov_q2_tot` | Coverage of social insurance programs in 2nd quintile (% of population) |
| `wdi_per_si_allsi_cov_q3_tot` | Coverage of social insurance programs in 3rd quintile (% of population) |
| `wdi_per_si_allsi_cov_q4_tot` | Coverage of social insurance programs in 4th quintile (% of population) |
| `wdi_per_si_allsi_cov_q5_tot` | Coverage of social insurance programs in richest quintile (% of population) |
| `wdi_px_rex_reer` | Real effective exchange rate index (2010 = 100) |
| `wdi_se_adt_1524_lt_fe_zs` | Literacy rate, youth female (% of females ages 15-24) |
| `wdi_se_adt_1524_lt_fm_zs` | Literacy rate, youth (ages 15-24), gender parity index (GPI) |
| `wdi_se_adt_1524_lt_ma_zs` | Literacy rate, youth male (% of males ages 15-24) |
| `wdi_se_adt_1524_lt_zs` | Literacy rate, youth total (% of people ages 15-24) |
| `wdi_se_adt_litr_fe_zs` | Literacy rate, adult female (% of females ages 15 and above) |
| `wdi_se_adt_litr_ma_zs` | Literacy rate, adult male (% of males ages 15 and above) |
| `wdi_se_adt_litr_zs` | Literacy rate, adult total (% of people ages 15 and above) |
| `wdi_se_com_durs` | Compulsory education, duration (years) |
| `wdi_se_enr_prim_fm_zs` | School enrollment, primary (gross), gender parity index (GPI) |
| `wdi_se_enr_prsc_fm_zs` | School enrollment, primary and secondary (gross), gender parity index (GPI) |
| `wdi_se_enr_seco_fm_zs` | School enrollment, secondary (gross), gender parity index (GPI) |
| `wdi_se_enr_tert_fm_zs` | School enrollment, tertiary (gross), gender parity index (GPI) |
| `wdi_se_lpv_prim` | Learning poverty: Share of Children at the End-of-Primary age below minimum reading proficiency adjusted by Out-of-School Children (%) |
| `wdi_se_lpv_prim_fe` | Learning poverty: Share of Female Children at the End-of-Primary age below minimum reading proficiency adjusted by Out-of-School Children (%) |
| `wdi_se_lpv_prim_ld` | Pupils below minimum reading proficiency at end of primary (%). Low GAML threshold |
| `wdi_se_lpv_prim_ld_fe` | Female pupils below minimum reading proficiency at end of primary (%). Low GAML threshold |
| `wdi_se_lpv_prim_ld_ma` | Male pupils below minimum reading proficiency at end of primary (%). Low GAML threshold |
| `wdi_se_lpv_prim_ma` | Learning poverty: Share of Male Children at the End-of-Primary age below minimum reading proficiency adjusted by Out-of-School Children (%) |
| `wdi_se_lpv_prim_sd` | Primary school age children out-of-school (%) |
| `wdi_se_lpv_prim_sd_fe` | Female primary school age children out-of-school (%) |
| `wdi_se_lpv_prim_sd_ma` | Male primary school age children out-of-school (%) |
| `wdi_se_pre_durs` | Preprimary education, duration (years) |
| `wdi_se_pre_enrl_tc_zs` | Pupil-teacher ratio, preprimary |
| `wdi_se_pre_enrr` | School enrollment, preprimary (% gross) |
| `wdi_se_pre_enrr_fe` | School enrollment, preprimary, female (% gross) |
| `wdi_se_pre_enrr_ma` | School enrollment, preprimary, male (% gross) |
| `wdi_se_pre_tcaq_fe_zs` | Trained teachers in preprimary education, female (% of female teachers) |
| `wdi_se_pre_tcaq_ma_zs` | Trained teachers in preprimary education, male (% of male teachers) |
| `wdi_se_pre_tcaq_zs` | Trained teachers in preprimary education (% of total teachers) |
| `wdi_se_prm_ages` | Primary school starting age (years) |
| `wdi_se_prm_cmpt_fe_zs` | Primary completion rate, female (% of relevant age group) |
| `wdi_se_prm_cmpt_ma_zs` | Primary completion rate, male (% of relevant age group) |
| `wdi_se_prm_cmpt_zs` | Primary completion rate, total (% of relevant age group) |
| `wdi_se_prm_cuat_fe_zs` | Educational attainment, at least completed primary, population 25+ years, female (%) (cumulative) |
| `wdi_se_prm_cuat_ma_zs` | Educational attainment, at least completed primary, population 25+ years, male (%) (cumulative) |
| `wdi_se_prm_cuat_zs` | Educational attainment, at least completed primary, population 25+ years, total (%) (cumulative) |
| `wdi_se_prm_durs` | Primary education, duration (years) |
| `wdi_se_prm_enrl` | Primary education, pupils |
| `wdi_se_prm_enrl_fe_zs` | Primary education, pupils (% female) |
| `wdi_se_prm_enrr` | School enrollment, primary (% gross) |
| `wdi_se_sec_enrr` | School enrollment, secondary (% gross) |
| `wdi_sg_dmk_alld_fn_zs` | Women participating in the three decisions (own health care, major household purchases, and visiting family) (% of women age 15-49) |
| `wdi_sg_dmk_srcr_fn_zs` | Women making their own informed decisions regarding sexual relations, contraceptive use and reproductive health care (% of women age 15-49) |
| `wdi_sg_gen_parl_zs` | Proportion of seats held by women in national parliaments (%) |
| `wdi_sg_tim_uwrk_fe` | Proportion of time spent on unpaid domestic and care work, female (% of 24 hour day) |
| `wdi_sg_tim_uwrk_ma` | Proportion of time spent on unpaid domestic and care work, male (% of 24 hour day) |
| `wdi_sg_vaw_1549_zs` | Proportion of women subjected to physical and/or sexual violence in the last 12 months (% of ever-partnered women ages 15-49) |
| `wdi_sg_vaw_argu_zs` | Women who believe a husband is justified in beating his wife when she argues with him (%) |
| `wdi_sg_vaw_burn_zs` | Women who believe a husband is justified in beating his wife when she burns the food (%) |
| `wdi_sg_vaw_goes_zs` | Women who believe a husband is justified in beating his wife when she goes out without telling him (%) |
| `wdi_sg_vaw_negl_zs` | Women who believe a husband is justified in beating his wife when she neglects the children (%) |
| `wdi_sg_vaw_reas_zs` | Women who believe a husband is justified in beating his wife (any of five reasons) (%) |
| `wdi_sg_vaw_refu_zs` | Women who believe a husband is justified in beating his wife when she refuses sex with him (%) |
| `wdi_sh_uhc_fh40` | Proportion of population facing financial hardship due to out-of-pocket health expenditure (%) |
| `wdi_sh_uhc_fh40_further` | Proportion of population further impoverished due to out-of-pocket health expenditure, based on the societal poverty line (%) |
| `wdi_sh_uhc_fh40_impov` | Proportion of population facing impoverishing out-of-pocket health expenditure, based on the societal poverty line (%) |
| `wdi_sh_uhc_fh40_large` | Proportion of population facing large (but non-impoverishing) out-of-pocket health expenditure (%) |
| `wdi_sh_uhc_fh40_pushed` | Proportion of population pushed into poverty (based on the societal poverty line) due to out-of-pocket health expenditure (%) |
| `wdi_sh_uhc_sci` | UHC service coverage index |
| `wdi_sh_uhc_sci_capacity` | UHC service coverage sub-index on service capacity and access |
| `wdi_sh_uhc_sci_id` | UHC service coverage sub-index on infectious diseases |
| `wdi_sh_uhc_sci_ncd` | UHC service coverage sub-index on non-communicable diseases |
| `wdi_sh_uhc_sci_rmnch` | UHC service coverage sub-index on reproductive, maternal, newborn and child health |
| `wdi_sh_alc_pcap_fe_li` | Total alcohol consumption per capita, female (liters of pure alcohol, projected estimates, female 15+ years of age) |
| `wdi_sh_alc_pcap_li` | Total alcohol consumption per capita (liters of pure alcohol, projected estimates, 15+ years of age) |
| `wdi_sh_alc_pcap_ma_li` | Total alcohol consumption per capita, male (liters of pure alcohol, projected estimates, male 15+ years of age) |
| `wdi_sh_anm_allw_zs` | Prevalence of anemia among women of reproductive age (% of women ages 15-49) |
| `wdi_sh_anm_chld_zs` | Prevalence of anemia among children (% of children ages 6-59 months) |
| `wdi_sh_anm_nprg_zs` | Prevalence of anemia among non-pregnant women (% of women ages 15-49) |
| `wdi_sh_con_1524_fe_zs` | Condom use, population ages 15-24, female (% of females ages 15-24) |
| `wdi_sh_con_1524_ma_zs` | Condom use, population ages 15-24, male (% of males ages 15-24) |
| `wdi_sh_dth_0509` | Number of deaths ages 5-9 years |
| `wdi_sh_dth_1014` | Number of deaths ages 10-14 years |
| `wdi_sh_dth_1519` | Number of deaths ages 15-19 years |
| `wdi_sh_dth_2024` | Number of deaths ages 20-24 years |
| `wdi_sh_dth_comm_zs` | Cause of death, by communicable diseases and maternal, prenatal and nutrition conditions (% of total) |
| `wdi_sh_dth_imrt` | Number of infant deaths |
| `wdi_sh_dth_injr_zs` | Cause of death, by injury (% of total) |
| `wdi_sh_dth_mort` | Number of under-five deaths |
| `wdi_sh_dth_ncom_zs` | Cause of death, by non-communicable diseases (% of total) |
| `wdi_sh_dth_nmrt` | Number of neonatal deaths |
| `wdi_sh_dth_ocvd_zs` | Cause of death, by other COVID-19 pandemic-related outcomes (% of total) |
| `wdi_sh_dyn_0509` | Probability of dying among children ages 5-9 years (per 1,000) |
| `wdi_sh_dyn_1014` | Probability of dying among adolescents ages 10-14 years (per 1,000) |
| `wdi_sh_dyn_1519` | Probability of dying among adolescents ages 15-19 years (per 1,000) |
| `wdi_sh_dyn_2024` | Probability of dying among youth ages 20-24 years (per 1,000) |
| `wdi_sh_dyn_aids_fe_zs` | Women's share of population ages 15+ living with HIV (%) |
| `wdi_sh_dyn_aids_zs` | Prevalence of HIV, total (% of population ages 15-49) |
| `wdi_sh_dyn_mort` | Mortality rate, under-5 (per 1,000 live births) |
| `wdi_sh_dyn_mort_fe` | Mortality rate, under-5, female (per 1,000 live births) |
| `wdi_sh_dyn_mort_ma` | Mortality rate, under-5, male (per 1,000 live births) |
| `wdi_sh_dyn_ncom_fe_zs` | Mortality from CVD, cancer, diabetes or CRD between exact ages 30 and 70, female (%) |
| `wdi_sh_dyn_ncom_ma_zs` | Mortality from CVD, cancer, diabetes or CRD between exact ages 30 and 70, male (%) |
| `wdi_sh_dyn_ncom_zs` | Mortality from CVD, cancer, diabetes or CRD between exact ages 30 and 70 (%) |
| `wdi_sh_dyn_nmrt` | Mortality rate, neonatal (per 1,000 live births) |
| `wdi_sh_fpl_satm_zs` | Demand for family planning satisfied by modern methods (% of married women with demand for family planning) |
| `wdi_sh_h2o_basw_ru_zs` | People using at least basic drinking water services, rural (% of rural population) |
| `wdi_sh_h2o_basw_ur_zs` | People using at least basic drinking water services, urban (% of urban population) |
| `wdi_sh_h2o_basw_zs` | People using at least basic drinking water services (% of population) |
| `wdi_sh_h2o_smdw_ru_zs` | People using safely managed drinking water services, rural (% of rural population) |
| `wdi_sh_h2o_smdw_ur_zs` | People using safely managed drinking water services, urban (% of urban population) |
| `wdi_sh_h2o_smdw_zs` | People using safely managed drinking water services (% of population) |
| `wdi_sh_sta_smss_zs` | People using safely managed sanitation services (% of population) |
| `wdi_sh_xpd_chex_gd_zs` | Current health expenditure (% of GDP) |
| `wdi_si_dst_02nd_20` | Income share held by second 20% |
| `wdi_si_dst_03rd_20` | Income share held by third 20% |
| `wdi_si_dst_04th_20` | Income share held by fourth 20% |
| `wdi_si_dst_05th_20` | Income share held by highest 20% |
| `wdi_si_dst_10th_10` | Income share held by highest 10% |
| `wdi_si_dst_50md` | Proportion of people living below 50 percent of median income (%) |
| `wdi_si_dst_frst_10` | Income share held by lowest 10% |
| `wdi_si_dst_frst_20` | Income share held by lowest 20% |
| `wdi_si_pov_dday` | Poverty headcount ratio at $3.00 a day (2021 PPP) (% of population) |
| `wdi_si_pov_gaps` | Poverty gap at $3.00 a day (2021 PPP) (%) |
| `wdi_si_pov_gini` | Gini index |
| `wdi_si_pov_lmic` | Poverty headcount ratio at $4.20 a day (2021 PPP) (% of population) |
| `wdi_si_pov_lmic_gp` | Poverty gap at $4.20 a day (2021 PPP) (%) |
| `wdi_si_pov_mpun` | Multidimensional poverty headcount ratio (UNDP & OPHI) (% of population) |
| `wdi_si_pov_mpwb` | Multidimensional poverty headcount ratio (World Bank) (% of population) |
| `wdi_si_pov_nahc` | Poverty headcount ratio at national poverty lines (% of population) |
| `wdi_si_pov_sopo` | Poverty headcount ratio at societal poverty line (% of population) |
| `wdi_si_pov_umic` | Poverty headcount ratio at $8.30 a day (2021 PPP) (% of population) |
| `wdi_si_pov_umic_gp` | Poverty gap at $8.30 a day (2021 PPP) (%) |
| `wdi_si_rmt_cost_ib_zs` | Average transaction cost of sending remittances to a specific country (%) |
| `wdi_si_rmt_cost_ob_zs` | Average transaction cost of sending remittances from a specific country (%) |
| `wdi_si_spr_pc40` | Survey mean consumption or income per capita, bottom 40% of population (2021 PPP $ per day) |
| `wdi_si_spr_pc40_zg` | Annualized average growth rate in per capita real survey mean consumption or income, bottom 40% of population (%) |
| `wdi_si_spr_pcap` | Survey mean consumption or income per capita, total population (2021 PPP $ per day) |
| `wdi_si_spr_pcap_zg` | Annualized average growth rate in per capita real survey mean consumption or income, total population (%) |
| `wdi_si_spr_pgap` | Prosperity gap (average shortfall from a prosperity standard of $28/day) |
| `wdi_sl_agr_0714_fe_zs` | Child employment in agriculture, female (% of female economically active children ages 7-14) |
| `wdi_sl_agr_0714_ma_zs` | Child employment in agriculture, male (% of male economically active children ages 7-14) |
| `wdi_sl_agr_0714_zs` | Child employment in agriculture (% of economically active children ages 7-14) |
| `wdi_sl_agr_empl_fe_zs` | Employment in agriculture, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_agr_empl_ma_zs` | Employment in agriculture, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_agr_empl_zs` | Employment in agriculture (% of total employment) (modeled ILO estimate) |
| `wdi_sl_emp_1524_sp_fe_ne_zs` | Employment to population ratio, ages 15-24, female (%) (national estimate) |
| `wdi_sl_emp_1524_sp_fe_zs` | Employment to population ratio, ages 15-24, female (%) (modeled ILO estimate) |
| `wdi_sl_emp_1524_sp_ma_ne_zs` | Employment to population ratio, ages 15-24, male (%) (national estimate) |
| `wdi_sl_emp_1524_sp_ma_zs` | Employment to population ratio, ages 15-24, male (%) (modeled ILO estimate) |
| `wdi_sl_emp_1524_sp_ne_zs` | Employment to population ratio, ages 15-24, total (%) (national estimate) |
| `wdi_sl_emp_1524_sp_zs` | Employment to population ratio, ages 15-24, total (%) (modeled ILO estimate) |
| `wdi_sl_emp_mpyr_fe_zs` | Employers, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_emp_mpyr_ma_zs` | Employers, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_emp_mpyr_zs` | Employers, total (% of total employment) (modeled ILO estimate) |
| `wdi_sl_emp_self_fe_zs` | Self-employed, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_emp_self_ma_zs` | Self-employed, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_emp_self_zs` | Self-employed, total (% of total employment) (modeled ILO estimate) |
| `wdi_sl_emp_smgt_fe_zs` | Female share of employment in senior and middle management (%) |
| `wdi_sl_emp_totl_sp_fe_ne_zs` | Employment to population ratio, 15+, female (%) (national estimate) |
| `wdi_sl_emp_totl_sp_fe_zs` | Employment to population ratio, 15+, female (%) (modeled ILO estimate) |
| `wdi_sl_emp_totl_sp_ma_ne_zs` | Employment to population ratio, 15+, male (%) (national estimate) |
| `wdi_sl_emp_totl_sp_ma_zs` | Employment to population ratio, 15+, male (%) (modeled ILO estimate) |
| `wdi_sl_emp_totl_sp_ne_zs` | Employment to population ratio, 15+, total (%) (national estimate) |
| `wdi_sl_emp_totl_sp_zs` | Employment to population ratio, 15+, total (%) (modeled ILO estimate) |
| `wdi_sl_emp_vuln_fe_zs` | Vulnerable employment, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_emp_vuln_ma_zs` | Vulnerable employment, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_emp_vuln_zs` | Vulnerable employment, total (% of total employment) (modeled ILO estimate) |
| `wdi_sl_emp_work_fe_zs` | Wage and salaried workers, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_emp_work_ma_zs` | Wage and salaried workers, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_emp_work_zs` | Wage and salaried workers, total (% of total employment) (modeled ILO estimate) |
| `wdi_sl_fam_0714_fe_zs` | Children in employment, unpaid family workers, female (% of female children in employment, ages 7-14) |
| `wdi_sl_fam_0714_ma_zs` | Children in employment, unpaid family workers, male (% of male children in employment, ages 7-14) |
| `wdi_sl_fam_0714_zs` | Children in employment, unpaid family workers (% of children in employment, ages 7-14) |
| `wdi_sl_fam_work_fe_zs` | Contributing family workers, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_fam_work_ma_zs` | Contributing family workers, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_fam_work_zs` | Contributing family workers, total (% of total employment) (modeled ILO estimate) |
| `wdi_sl_gdp_pcap_em_kd` | GDP per person employed (constant 2021 PPP $) |
| `wdi_sl_ind_empl_fe_zs` | Employment in industry, female (% of female employment) (modeled ILO estimate) |
| `wdi_sl_ind_empl_ma_zs` | Employment in industry, male (% of male employment) (modeled ILO estimate) |
| `wdi_sl_uem_totl_zs` | Unemployment, total (% of total labor force) (modeled ILO estimate) |
| `wdi_sm_pop_asys_ea` | Asylum-seekers by country or territory of asylum |
| `wdi_sm_pop_asys_eo` | Asylum-seekers by country or territory of origin |
| `wdi_sm_pop_fdip` | Forcibly displaced people |
| `wdi_sm_pop_idpc` | Internally displaced people (IDP) by country or territory of asylum / origin |
| `wdi_sm_pop_netm` | Net migration |
| `wdi_sm_pop_opip_ea` | Other people in need of international protection by country or territory of asylum |
| `wdi_sm_pop_opip_eo` | Other people in need of international protection by country or territory of origin |
| `wdi_sm_pop_rhcr_ea` | Refugees under the mandate of the UNHCR by country or territory of asylum |
| `wdi_sm_pop_rhcr_eo` | Refugees under the mandate of the UNHCR by country or territory of origin |
| `wdi_sm_pop_rrwa_ea` | Refugees under the mandate of the UNRWA by country or territory of asylum |
| `wdi_sm_pop_rrwa_eo` | Refugees under the mandate of the UNRWA by country or territory of origin |
| `wdi_sm_pop_totl` | International migrant stock, total |
| `wdi_sm_pop_totl_zs` | International migrant stock (% of population) |
| `wdi_sn_itk_defc_zs` | Prevalence of undernourishment (% of population) |
| `wdi_sn_itk_msfi_zs` | Prevalence of moderate or severe food insecurity in the population (%) |
| `wdi_sn_itk_salt_zs` | Consumption of iodized salt (% of households) |
| `wdi_sn_itk_svfi_zs` | Prevalence of severe food insecurity in the population (%) |
| `wdi_sn_itk_vita_zs` | Vitamin A supplementation coverage rate (% of children ages 6-59 months) |
| `wdi_sp_ado_tfrt` | Adolescent fertility rate (births per 1,000 women ages 15-19) |
| `wdi_sp_dyn_amrt_fe` | Mortality rate, adult, female (per 1,000 female adults) |
| `wdi_sp_dyn_amrt_ma` | Mortality rate, adult, male (per 1,000 male adults) |
| `wdi_sp_dyn_cbrt_in` | Birth rate, crude (per 1,000 people) |
| `wdi_sp_dyn_cdrt_in` | Death rate, crude (per 1,000 people) |
| `wdi_sp_dyn_conm_zs` | Contraceptive prevalence, any modern method (% of married women ages 15-49) |
| `wdi_sp_dyn_conu_zs` | Contraceptive prevalence, any method (% of married women ages 15-49) |
| `wdi_sp_dyn_imrt_fe_in` | Mortality rate, infant, female (per 1,000 live births) |
| `wdi_sp_dyn_imrt_in` | Mortality rate, infant (per 1,000 live births) |
| `wdi_sp_dyn_imrt_ma_in` | Mortality rate, infant, male (per 1,000 live births) |
| `wdi_sp_dyn_le00_fe_in` | Life expectancy at birth, female (years) |
| `wdi_sp_dyn_le00_in` | Life expectancy at birth, total (years) |
| `wdi_sp_dyn_le00_ma_in` | Life expectancy at birth, male (years) |
| `wdi_sp_dyn_tfrt_in` | Fertility rate, total (births per woman) |
| `wdi_sp_dyn_to65_fe_zs` | Survival to age 65, female (% of cohort) |
| `wdi_sp_dyn_to65_ma_zs` | Survival to age 65, male (% of cohort) |
| `wdi_sp_dyn_wfrt` | Wanted fertility rate (births per woman) |
| `wdi_sp_hou_fema_zs` | Female headed households (% of households with a female head) |
| `wdi_sp_m15_2024_fe_zs` | Women who were first married by age 15 (% of women ages 20-24) |
| `wdi_sp_m18_2024_fe_zs` | Women who were first married by age 18 (% of women ages 20-24) |
| `wdi_sp_mtr_1519_zs` | Teenage mothers (% of women ages 15-19 who have had children or are currently pregnant) |
| `wdi_sp_pop_0004_fe_5y` | Population ages 00-04, female (% of female population) |
| `wdi_sp_pop_0004_ma_5y` | Population ages 00-04, male (% of male population) |
| `wdi_sp_pop_0014_fe_in` | Population ages 0-14, female |
| `wdi_sp_pop_0014_fe_zs` | Population ages 0-14, female (% of female population) |
| `wdi_sp_pop_0014_ma_in` | Population ages 0-14, male |
| `wdi_sp_pop_0014_ma_zs` | Population ages 0-14, male (% of male population) |
| `wdi_sp_pop_0014_to` | Population ages 0-14, total |
| `wdi_sp_pop_0014_to_zs` | Population ages 0-14 (% of total population) |
| `wdi_sp_pop_0509_fe_5y` | Population ages 05-09, female (% of female population) |
| `wdi_sp_pop_0509_ma_5y` | Population ages 05-09, male (% of male population) |
| `wdi_sp_pop_1014_fe_5y` | Population ages 10-14, female (% of female population) |
| `wdi_sp_pop_1014_ma_5y` | Population ages 10-14, male (% of male population) |
| `wdi_sp_pop_1519_fe_5y` | Population ages 15-19, female (% of female population) |
| `wdi_sp_pop_1519_ma_5y` | Population ages 15-19, male (% of male population) |
| `wdi_sp_pop_1564_fe_in` | Population ages 15-64, female |
| `wdi_sp_pop_1564_fe_zs` | Population ages 15-64, female (% of female population) |
| `wdi_sp_pop_1564_ma_in` | Population ages 15-64, male |
| `wdi_sp_pop_1564_ma_zs` | Population ages 15-64, male (% of male population) |
| `wdi_sp_pop_1564_to` | Population ages 15-64, total |
| `wdi_sp_pop_totl` | Population, total |
| `wdi_st_int_arvl` | International tourism, number of arrivals |
| `wdi_st_int_dprt` | International tourism, number of departures |
| `wdi_st_int_rcpt_cd` | International tourism, receipts (current US$) |
| `wdi_st_int_rcpt_xp_zs` | International tourism, receipts (% of total exports) |
| `wdi_st_int_trnr_cd` | International tourism, receipts for passenger transport items (current US$) |
| `wdi_st_int_trnx_cd` | International tourism, expenditures for passenger transport items (current US$) |
| `wdi_st_int_tvlr_cd` | International tourism, receipts for travel items (current US$) |
| `wdi_st_int_tvlx_cd` | International tourism, expenditures for travel items (current US$) |
| `wdi_st_int_xpnd_cd` | International tourism, expenditures (current US$) |
| `wdi_st_int_xpnd_mp_zs` | International tourism, expenditures (% of total imports) |
| `wdi_tg_val_totl_gd_zs` | Merchandise trade (% of GDP) |
| `wdi_tm_qty_mrch_xd_wd` | Import volume index (2015 = 100) |
| `wdi_tm_tax_manf_bc_zs` | Binding coverage, manufactured products (%) |
| `wdi_tm_tax_manf_br_zs` | Bound rate, simple mean, manufactured products (%) |
| `wdi_tm_tax_manf_sm_ar_zs` | Tariff rate, applied, simple mean, manufactured products (%) |
| `wdi_tm_tax_manf_sm_fn_zs` | Tariff rate, most favored nation, simple mean, manufactured products (%) |
| `wdi_tm_tax_manf_wm_ar_zs` | Tariff rate, applied, weighted mean, manufactured products (%) |
| `wdi_tm_tax_manf_wm_fn_zs` | Tariff rate, most favored nation, weighted mean, manufactured products (%) |
| `wdi_tm_tax_mrch_bc_zs` | Binding coverage, all products (%) |
| `wdi_tm_tax_mrch_br_zs` | Bound rate, simple mean, all products (%) |
| `wdi_tm_tax_mrch_sm_ar_zs` | Tariff rate, applied, simple mean, all products (%) |
| `wdi_tm_tax_mrch_sm_fn_zs` | Tariff rate, most favored nation, simple mean, all products (%) |
| `wdi_tm_tax_mrch_wm_ar_zs` | Tariff rate, applied, weighted mean, all products (%) |
| `wdi_tm_tax_mrch_wm_fn_zs` | Tariff rate, most favored nation, weighted mean, all products (%) |
| `wdi_tm_tax_tcom_bc_zs` | Binding coverage, primary products (%) |
| `wdi_tm_tax_tcom_br_zs` | Bound rate, simple mean, primary products (%) |
| `wdi_tm_tax_tcom_sm_ar_zs` | Tariff rate, applied, simple mean, primary products (%) |
| `wdi_tm_tax_tcom_sm_fn_zs` | Tariff rate, most favored nation, simple mean, primary products (%) |
| `wdi_tm_tax_tcom_wm_ar_zs` | Tariff rate, applied, weighted mean, primary products (%) |
| `wdi_tm_tax_tcom_wm_fn_zs` | Tariff rate, most favored nation, weighted mean, primary products (%) |
| `wdi_tm_uvi_mrch_xd_wd` | Import unit value index (2015 = 100) |
| `wdi_tm_val_ictg_zs_un` | ICT goods imports (% total goods imports) |
| `wdi_tm_val_insf_zs_wt` | Insurance and financial services (% of commercial service imports) |
| `wdi_tm_val_mrch_al_zs` | Merchandise imports from economies in the Arab World (% of total merchandise imports) |
| `wdi_tm_val_mrch_cd_wt` | Merchandise imports (current US$) |
| `wdi_tm_val_mrch_hi_zs` | Merchandise imports from high-income economies (% of total merchandise imports) |
| `wdi_tm_val_mrch_or_zs` | Merchandise imports from low- and middle-income economies outside region (% of total merchandise imports) |
| `wdi_tm_val_mrch_r1_zs` | Merchandise imports from low- and middle-income economies in East Asia & Pacific (% of total merchandise imports) |
| `wdi_tm_val_mrch_r2_zs` | Merchandise imports from low- and middle-income economies in Europe & Central Asia (% of total merchandise imports) |
| `wdi_tm_val_mrch_r3_zs` | Merchandise imports from low- and middle-income economies in Latin America & the Caribbean (% of total merchandise imports) |
| `wdi_tm_val_mrch_r4_zs` | Merchandise imports from low- and middle-income economies in Middle East & North Africa (% of total merchandise imports) |
| `wdi_tm_val_mrch_r5_zs` | Merchandise imports from low- and middle-income economies in South Asia (% of total merchandise imports) |
| `wdi_tm_val_mrch_r6_zs` | Merchandise imports from low- and middle-income economies in Sub-Saharan Africa (% of total merchandise imports) |
| `wdi_tm_val_mrch_rs_zs` | Merchandise imports by the reporting economy, residual (% of total merchandise imports) |
| `wdi_tm_val_mrch_wl_cd` | Merchandise imports by the reporting economy (current US$) |
| `wdi_tm_val_mrch_wr_zs` | Merchandise imports from low- and middle-income economies within region (% of total merchandise imports) |
| `wdi_tm_val_mrch_xd_wd` | Import value index (2015 = 100) |
| `wdi_tm_val_othr_zs_wt` | Computer, communications and other services (% of commercial service imports) |
| `wdi_tm_val_serv_cd_wt` | Commercial service imports (current US$) |
| `wdi_tm_val_tran_zs_wt` | Transport services (% of commercial service imports) |
| `wdi_tm_val_trvl_zs_wt` | Travel services (% of commercial service imports) |
| `wdi_tt_pri_mrch_xd_wd` | Net barter terms of trade index (2015 = 100) |
| `wdi_tx_mnf_tech_zs_un` | Medium and high-tech exports (% manufactured exports) |
| `wdi_tx_qty_mrch_xd_wd` | Export volume index (2015 = 100) |
| `wdi_tx_uvi_mrch_xd_wd` | Export unit value index (2015 = 100) |
| `wdi_tx_val_agri_zs_un` | Agricultural raw materials exports (% of merchandise exports) |
| `wdi_tx_val_food_zs_un` | Food exports (% of merchandise exports) |
| `wdi_tx_val_fuel_zs_un` | Fuel exports (% of merchandise exports) |
| `wdi_tx_val_ictg_zs_un` | ICT goods exports (% of total goods exports) |
| `wdi_tx_val_insf_zs_wt` | Insurance and financial services (% of commercial service exports) |
| `wdi_tx_val_manf_zs_un` | Manufactures exports (% of merchandise exports) |
| `wdi_tx_val_mmtl_zs_un` | Ores and metals exports (% of merchandise exports) |
| `wdi_tx_val_mrch_al_zs` | Merchandise exports to economies in the Arab World (% of total merchandise exports) |
| `wdi_tx_val_mrch_cd_wt` | Merchandise exports (current US$) |
| `wdi_tx_val_mrch_hi_zs` | Merchandise exports to high-income economies (% of total merchandise exports) |
| `wdi_tx_val_mrch_or_zs` | Merchandise exports to low- and middle-income economies outside region (% of total merchandise exports) |
| `wdi_tx_val_mrch_r1_zs` | Merchandise exports to low- and middle-income economies in East Asia & Pacific (% of total merchandise exports) |
| `wdi_tx_val_mrch_r2_zs` | Merchandise exports to low- and middle-income economies in Europe & Central Asia (% of total merchandise exports) |
| `wdi_tx_val_mrch_r3_zs` | Merchandise exports to low- and middle-income economies in Latin America & the Caribbean (% of total merchandise exports) |
| `wdi_tx_val_mrch_r4_zs` | Merchandise exports to low- and middle-income economies in Middle East & North Africa (% of total merchandise exports) |
| `wdi_tx_val_mrch_r5_zs` | Merchandise exports to low- and middle-income economies in South Asia (% of total merchandise exports) |
| `wdi_tx_val_mrch_r6_zs` | Merchandise exports to low- and middle-income economies in Sub-Saharan Africa (% of total merchandise exports) |
| `wdi_tx_val_mrch_rs_zs` | Merchandise exports by the reporting economy, residual (% of total merchandise exports) |
| `wdi_tx_val_mrch_wl_cd` | Merchandise exports by the reporting economy (current US$) |
| `wdi_tx_val_mrch_wr_zs` | Merchandise exports to low- and middle-income economies within region (% of total merchandise exports) |
| `wdi_tx_val_mrch_xd_wd` | Export value index (2015 = 100) |
| `wdi_tx_val_othr_zs_wt` | Computer, communications and other services (% of commercial service exports) |
| `wdi_tx_val_serv_cd_wt` | Commercial service exports (current US$) |
| `wdi_tx_val_tech_cd` | High-technology exports (current US$) |
| `wdi_tx_val_tech_mf_zs` | High-technology exports (% of manufactured exports) |
| `wdi_tx_val_tran_zs_wt` | Transport services (% of commercial service exports) |
| `wdi_tx_val_trvl_zs_wt` | Travel services (% of commercial service exports) |
| `wdi_vc_btl_deth` | Battle-related deaths (number of people) |
| `wdi_vc_idp_nwcv` | Internally displaced persons, new displacement associated with conflict and violence (number of cases) |
| `wdi_vc_idp_nwds` | Internally displaced persons, new displacement associated with disasters (number of cases) |
| `wdi_vc_ihr_psrc_fe_p5` | Intentional homicides, female (per 100,000 female) |
| `wdi_vc_ihr_psrc_ma_p5` | Intentional homicides, male (per 100,000 male) |
| `wdi_vc_ihr_psrc_p5` | Intentional homicides (per 100,000 people) |
