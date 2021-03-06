package org.cggh.alfresco.ssr.action;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.repo.content.transform.ContentTransformerWorker;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowPath;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.PropertyMap;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.cggh.alfresco.ssr.model.SSRWorkflowModel;
import org.cggh.model.CGGHContentModel;
import org.cggh.model.CGGHNamespaceService;

import com.ibm.icu.util.GregorianCalendar;

public class SampleStatusReportReader extends ActionExecuterAbstractBase {

	private static final String DEFAULT_NAMECHECK_GROUP = "GROUP_COLLABORATION_MANAGERS";
	private static final String DEFAULT_NAMECHECK_TASK = "activiti$nameMismatch:2:204";
	private static final String DEFAULT_COUNTCHECK_TASK = "activiti$sampleCountExceeded:1:904";
	private static final String DEFAULT_STATUSCHECKTASK = "activiti$statusCheck:2:204";
	private static final String DEFAULT_COUNTRYCHECKTASK = "activiti$countryCheck:2:204";
	private static final String DEFAULT_DATECHECKTASK = "activiti$dateCheck:2:204";
	private static final String DEFAULT_COLLABDOCTASK = "activiti$collabDocCheck:2:204";
	
	private static final Boolean DEFAULT_TRANSFORM = true;
	private static final String PARAM_TRANSFORM = "transform";
	private static final String PARAM_NAMECHECKTASK = "nameCheckTask";
	private static final String PARAM_COUNTCHECKTASK = "countCheckTask";
	private static final String PARAM_TASK_GROUP = "nameCheckGroup";
	private static final int COUNTRY_COLUMN = 4;
	private static final int DATE_COLUMN = 12;
	private static final String PARAM_STATUSCHECKTASK = "statusCheckTask";
	private static final String PARAM_COUNTRYCHECKTASK = "countryCheckTask";
	private static final String PARAM_DATECHECKTASK = "dateCheckTask";
	private static final String PARAM_COLLABDOCTASK = "collabDocTask";
	

	private static Log logger = LogFactory.getLog(SampleStatusReportReader.class);

	private NodeService nodeService;
	private SearchService searchService;
	private ContentService contentService;
	private WorkflowService workflowService;
	private AuthorityService authorityService;

	private ContentTransformerWorker transformer;

	private boolean transform;
	private String nameCheckTask;
	private String nameCheckGroup;
	private String collabReportName = "SampleStatusReport";
	private DictionaryService dictionaryService;
	private String countCheckTask;
	private boolean autoUpdateCountries = false;
	private String statusCheckTask;
	private String dateCheckTask;
	private String countryCheckTask;
	private String collabDocTask;
	
	protected Date getFirstExpected(NodeRef collabNode) {
		Date expected = DefaultTypeConverter.INSTANCE.convert(Date.class,
				nodeService.getProperty(collabNode, CGGHContentModel.PROP_FIRST_SAMPLE_EXPECTED));

		if (expected == null) {
			expected = new Date(0);
		}

		return expected;
	}

	protected Date getLastExpected(NodeRef collabNode) {
		Date expected = DefaultTypeConverter.INSTANCE.convert(Date.class,
				nodeService.getProperty(collabNode, CGGHContentModel.PROP_LAST_SAMPLE_EXPECTED));
		if (expected == null) {
			expected = new GregorianCalendar(2222, 1, 1).getTime();
		}
		return expected;
	}

	protected int getExpected(NodeRef collabNode) {
		Integer expected = DefaultTypeConverter.INSTANCE.convert(Integer.class,
				nodeService.getProperty(collabNode, CGGHContentModel.PROP_SAMPLES_EXPECTED));

		/*
		 * if (logger.isDebugEnabled()) { logger.debug("found expected: " +
		 * expected); }
		 */
		if (expected == null) {
			return 0;
		}
		return expected;
	}

	protected int getProcessed(NodeRef collabNode) {
		Integer processed = DefaultTypeConverter.INSTANCE.convert(Integer.class,
				nodeService.getProperty(collabNode, CGGHContentModel.PROP_SAMPLES_PROCESSED));

		/*
		 * if (logger.isDebugEnabled()) { logger.debug("found processed: " +
		 * processed); }
		 */
		if (processed == null) {
			return 0;
		}
		return processed;
	}

	protected List<String> getCountries(NodeRef collabNode) {
		@SuppressWarnings("unchecked")
		List<String> countries = (List<String>) nodeService.getProperty(collabNode,
				CGGHContentModel.PROP_SAMPLE_COUNTRIES);

		if (countries == null) {
			countries = new ArrayList<String>();
		}
		return countries;
	}

	protected void setCountries(NodeRef collabNode, List<String> countries) {
		nodeService.setProperty(collabNode, CGGHContentModel.PROP_SAMPLE_COUNTRIES, (Serializable) countries);
	}

	protected NodeRef getCollaboration(String alfrescoCode, boolean partialMatch) {
		StoreRef storeRef = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "SpacesStore");
		// String query = "TYPE:cm\\:person +@cm\\:email:\"" + from + "\"";
		String query = "TYPE:" + CGGHNamespaceService.COLLAB_MODEL_PREFIX + "\\:"
				+ CGGHContentModel.TYPE_COLLAB_FOLDER.getLocalName() + " AND (cm\\:name:\"" + alfrescoCode + "\")";

		ResultSet resultSet = searchService.query(storeRef, SearchService.LANGUAGE_FTS_ALFRESCO, query);
		try {
			if (resultSet.length() == 0) {
				return null;
			} else {
				for (int resNum = 0; resNum < resultSet.length(); resNum++) {
					NodeRef collabNode = resultSet.getNodeRef(resNum);
					if (nodeService.exists(collabNode)) {
						String name = DefaultTypeConverter.INSTANCE.convert(String.class,
								nodeService.getProperty(collabNode, ContentModel.PROP_NAME));
						if (partialMatch) {
							if (name.startsWith(alfrescoCode)) {
								return collabNode;
							}
						} else if (name.equals(alfrescoCode)) {
							return collabNode;
						}

					} else {
						// The Lucene index returned a dead result
					}
				}
			}
		} finally {
			if (resultSet != null) {
				resultSet.close();
			}
		}
		return null;
	}

	public void startWorkflowTask(NodeRef collabNode, String otherName) {

		String description = "Collaboration ID mismatch:" + otherName;

		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode, description);

		params.put(SSRWorkflowModel.PROP_REPORT_NAME, otherName);
		WorkflowPath path = workflowService.startWorkflow(nameCheckTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);

	}

	public void startCountWorkflowTask(NodeRef collabNode, String description, int expected, int count) {
		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode, description);

		params.put(SSRWorkflowModel.PROP_REPORT_SAMPLES_COUNT, count);
		params.put(SSRWorkflowModel.PROP_EXPECTED_SAMPLES_COUNT, expected);

		WorkflowPath path = workflowService.startWorkflow(countCheckTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);

	}

	public void startCollabDocWorkflowTask(NodeRef collabNode) {
		String description = "No collaboration document";
		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode, description);

		WorkflowPath path = workflowService.startWorkflow(collabDocTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);

	}
	
	Map<QName, Serializable> createCommonWorkflowParams(NodeRef collabNode, String description) {
		String nodeName = DefaultTypeConverter.INSTANCE.convert(String.class,
				nodeService.getProperty(collabNode, ContentModel.PROP_NAME));
		// Create workflow parameters
		Map<QName, Serializable> params = new HashMap<QName, Serializable>();
		NodeRef wfPackage = workflowService.createPackage(null);

		params.put(WorkflowModel.ASSOC_PACKAGE, wfPackage);

		NodeRef group = authorityService.getAuthorityNodeRef(nameCheckGroup);
		params.put(WorkflowModel.ASSOC_GROUP_ASSIGNEE, group);

		params.put(WorkflowModel.PROP_WORKFLOW_DESCRIPTION, nodeName + ":" + description);

		nodeService.addChild(wfPackage, collabNode, ContentModel.ASSOC_CONTAINS,
				QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, "samplestatus"));

		return params;
	}

	private static void copyRow(HashMap<Integer, HSSFCellStyle> styleCache, HSSFWorkbook xlswb, HSSFSheet resultSheet,
			HSSFRow sourceRow, int rownum) {
		/*
		 * if (logger.isDebugEnabled()) { logger.debug("Creating row:" +
		 * rownum); }
		 */
		HSSFRow newRow = resultSheet.createRow(rownum);

		if (sourceRow == null) {
			return;
		}

		newRow.setHeight(sourceRow.getHeight());
		int cols = sourceRow.getLastCellNum();
		// Loop through source columns to add to new row
		for (int i = 0; i < cols; i++) {
			// Grab a copy of the old/new cell
			HSSFCell oldCell = sourceRow.getCell(i);
			HSSFCell newCell = newRow.createCell(i);

			// If the old cell is null jump to next cell
			if (oldCell == null) {
				newCell = null;
				continue;
			}

			// There's a limit to the number of styles so need to cache
			HSSFCellStyle oldStyle = oldCell.getCellStyle();

			HSSFCellStyle newStyle = styleCache.get(oldStyle.hashCode());

			if (newStyle == null) {
				HSSFCellStyle newCellStyle = xlswb.createCellStyle();
				// Copy style from old cell and apply to new cell
				newCellStyle.cloneStyleFrom(oldCell.getCellStyle());
				styleCache.put(oldStyle.hashCode(), newCellStyle);
				newStyle = newCellStyle;
			}

			newCell.setCellStyle(newStyle);

			// If there is a cell comment, copy
			if (oldCell.getCellComment() != null) {
				newCell.setCellComment(oldCell.getCellComment());
			}

			// If there is a cell hyperlink, copy
			if (oldCell.getHyperlink() != null) {
				newCell.setHyperlink(oldCell.getHyperlink());
			}

			// Set the cell data type
			newCell.setCellType(oldCell.getCellType());

			// Set the cell data value
			switch (oldCell.getCellType()) {
			case Cell.CELL_TYPE_BLANK:
				newCell.setCellValue(oldCell.getStringCellValue());
				break;
			case Cell.CELL_TYPE_BOOLEAN:
				newCell.setCellValue(oldCell.getBooleanCellValue());
				break;
			case Cell.CELL_TYPE_ERROR:
				newCell.setCellErrorValue(oldCell.getErrorCellValue());
				break;
			case Cell.CELL_TYPE_FORMULA:
				newCell.setCellFormula(oldCell.getCellFormula());
				break;
			case Cell.CELL_TYPE_NUMERIC:
				newCell.setCellValue(oldCell.getNumericCellValue());
				break;
			case Cell.CELL_TYPE_STRING:
				newCell.setCellValue(oldCell.getRichStringCellValue());
				break;
			}
		}
		/*
		 * if (logger.isDebugEnabled()) { logger.debug("Last row:" +
		 * resultSheet.getLastRowNum()); }
		 */
	}

	public void process(InputStream file) throws IOException {
		HashMap<String, NodeRef> studyReports = new HashMap<String, NodeRef>();
		// Get the workbook instance for XLS file
		HSSFWorkbook workbook = new HSSFWorkbook(file);

		// Get first sheet from the workbook
		HSSFSheet sheet = workbook.getSheetAt(0);

		// Get iterator to all the rows in current sheet
		Iterator<Row> rowIterator = sheet.iterator();

		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			String alfrescoCode = row.getCell(2).getStringCellValue();
			if (alfrescoCode.equals("Alfresco Code")) {
				// System.out.println("Row num:" + row.getRowNum());
			} else if (alfrescoCode.length() > 3) {
				String codeNum = alfrescoCode.substring(0, 4);
				try {
					Integer.parseInt(codeNum);
					NodeRef collabNode = getCollaboration(alfrescoCode, false);
					if (collabNode == null) {
						logger.debug("Collaboration not found:" + alfrescoCode);
						collabNode = getCollaboration(codeNum, true);
						if (collabNode != null) {
							if (logger.isDebugEnabled()) {
								String nodeName = DefaultTypeConverter.INSTANCE.convert(String.class,
										nodeService.getProperty(collabNode, ContentModel.PROP_NAME));
								logger.debug("Collaboration name mismatch:" + nodeName + ":" + alfrescoCode);
							}
							if (!isTaskActive(collabNode, nameCheckTask)) {
								startWorkflowTask(collabNode, alfrescoCode);
							}
						} else {
							continue;
						}
					} else {
						String nodeName = DefaultTypeConverter.INSTANCE.convert(String.class,
								nodeService.getProperty(collabNode, ContentModel.PROP_NAME));
						if (!nodeName.equals(alfrescoCode)) {
							logger.debug("Collaboration name mismatch:" + nodeName + ":" + alfrescoCode);
							if (!isTaskActive(collabNode, nameCheckTask)) {
								startWorkflowTask(collabNode, alfrescoCode);
							}
						}
					}
					int expected = getExpected(collabNode);
					int processed = getProcessed(collabNode);
					boolean newSamples = false;

					double sangerYes = row.getCell(5).getNumericCellValue();
					//double sangerNo = row.getCell(4).getNumericCellValue();
					double sangerSequenced = row.getCell(6).getNumericCellValue();
					//int sangerSystemTotal = (int) (sangerNo + sangerYes);
					// Only do something if the number processed has changed
					if (processed != sangerYes) {

						newSamples = true;

						setSequenced(collabNode, (int) sangerSequenced);
						//Processed is really submitted to sequencing
						setProcessed(collabNode, (int) sangerYes);
						if (logger.isDebugEnabled()) {
							logger.debug("Updated samples for:" + alfrescoCode + " expected:" + expected + " processed:"
									+ sangerYes);
						}

						if (sangerYes > expected) {
							//Could/Should be part of CollaborationFolder behaviour
							//but here because the other tasks can't/shouldn't be
							String description = "More samples than expected:" + expected + " processed:"
									+ sangerYes;
							if (logger.isDebugEnabled()) {
								logger.debug(description);
							}
							if (!isTaskActive(collabNode, countCheckTask)) {
								startCountWorkflowTask(collabNode, description, expected, (int) sangerYes);
							}
						}

						String status = getCollaborationStatus(collabNode);

						if (!status.equals("active")) {
							//This bit is to avoid too many tasks being generated on first run
							Date last = getLastExpected(collabNode);
							Date cutoff = new GregorianCalendar(2014, 11, 1).getTime();
							if (!(status.equals("closed") && last != null && last.before(cutoff))) {
								if (!isTaskActive(collabNode, statusCheckTask)) {
									startStatusWorkflowTask(collabNode);
								}
							}
						}
						
						if(!hasCollaborationDocument(collabNode)) {
							if (!isTaskActive(collabNode, collabDocTask)) {
								startCollabDocWorkflowTask(collabNode);
							}
						}
					}

					Hyperlink report = row.getCell(1).getHyperlink();
					String addr = report.getAddress();
					if (addr != null) {
						String[] parts = addr.split("!");
						if (newSamples) {
							studyReports.put(parts[0], collabNode);
						}
					}
				} catch (NumberFormatException nfe) {
					// ignored
				}
			}
		}

		for (String key : studyReports.keySet()) {
			processStudy(workbook, key, studyReports.get(key));
		}
	}

	private boolean hasCollaborationDocument(NodeRef collabNode) {
		List<AssociationRef> docNodes = nodeService.getTargetAssocs(collabNode, CGGHContentModel.ASSOC_COLLABORATION_DOC);
		
		return !docNodes.isEmpty();
	}

	private String getCollaborationStatus(NodeRef collabNode) {
		String status = (String) nodeService.getProperty(collabNode, CGGHContentModel.PROP_COLLAB_STATUS);

		if (status == null) {
			status = "";
		}
		return status;
	}

	private void processStudy(HSSFWorkbook workbook, String sheetName, NodeRef collabNode) throws IOException {
		HSSFSheet reportSheet = workbook.getSheet(sheetName);
		List<String> countryProps = getCountries(collabNode);
		List<String> sampleCountries = new ArrayList<String>();
		boolean addedCountry = false;
		Date firstDate = null;
		Date lastDate = null;
		if (reportSheet != null) {
			HSSFWorkbook xlswb = new HSSFWorkbook();
			HSSFSheet resultSheet = xlswb.createSheet();

			resultSheet.getPrintSetup().setLandscape(reportSheet.getPrintSetup().getLandscape());
			// Not entirely sure why this needs to be done like this

			int rowEnd = reportSheet.getLastRowNum();

			// Can't use as skips blank rows
			// Iterator<Row> copyIterator = reportSheet.iterator();
			short maxCols = 0;
			HashMap<Integer, HSSFCellStyle> styleCache = new HashMap<Integer, HSSFCellStyle>();
			boolean countries = false;
			for (int rowNum = 0; rowNum <= rowEnd; rowNum++) {
				Row sourceRow = reportSheet.getRow(rowNum);
				SampleStatusReportReader.copyRow(styleCache, xlswb, resultSheet, (HSSFRow) sourceRow, rowNum);
				if (sourceRow != null) {
					short cols = sourceRow.getLastCellNum();
					if (maxCols < cols) {
						maxCols = cols;
					}
				}
				if (sourceRow != null) {
					if (countries) {
						Cell dateCell = sourceRow.getCell(DATE_COLUMN);
						if (dateCell != null && dateCell.getCellType() == Cell.CELL_TYPE_STRING) {
							String value = dateCell.getStringCellValue();
							SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
							try {
								Date cDate = format.parse(value);
								if (firstDate == null) {
									firstDate = cDate;
								} else if (cDate.before(firstDate)) {
									firstDate = cDate;
								}
								if (lastDate == null) {
									lastDate = cDate;
								} else if (cDate.after(lastDate)) {
									lastDate = cDate;
								}
							} catch (ParseException e) {
								if (logger.isDebugEnabled()) {
									logger.debug("", e);
								}
							}

						}
					}
					Cell countryCell = sourceRow.getCell(COUNTRY_COLUMN);
					if (countryCell != null && countryCell.getCellType() == Cell.CELL_TYPE_STRING) {
						if (countries) {
							String country = countryCell.getStringCellValue();
							String mappedCountry = country.toUpperCase();
							if (country.equals("Gambia, The")) {
								mappedCountry = "GAMBIA";
							} else if (country.equals("Vietnam")) {
								mappedCountry = "VIET NAM";
							} else if (country.equals("Cote d'Ivoire (Ivory Coast)")) {
								mappedCountry = "CÔTE D'IVOIRE";
							} else if (country.equals("Myanmar (Burma)")) {
								mappedCountry = "MYANMAR";
							} else if (country.equals("Tanzania")) {
								mappedCountry = "TANZANIA (UNITED REPUBLIC OF)";
							} else if (country.equals("Democratic Republic of Congo")) {
								mappedCountry = "CONGO (THE DEMOCRATIC REPUBLIC OF THE)";
							} else if (country.equals("Laos")) {
								mappedCountry = "LAO PEOPLE'S DEMOCRATIC REPUBLIC";
							} else if (country.equals("Korea, South")) {
								mappedCountry = "KOREA (REPUBLIC OF)";
							} else if (country.equals("Iran")) {
								mappedCountry = "IRAN (ISLAMIC REPUBLIC OF)";
							} else if (country.equals("China, People's Republic of")) {
								mappedCountry = "CHINA";
							}
							if (!sampleCountries.contains(mappedCountry)) {
								sampleCountries.add(mappedCountry);
							}
						}
						if (countryCell.getStringCellValue().equals("Country of Origin")) {
							countries = true;
						}

					}
				}
			}

			boolean countryTaskRequired = false;
			ArrayList<String> noSampleCountries = new ArrayList<String>();
			for (String cntry : countryProps) {
				if (!sampleCountries.contains(cntry)) {
					if (logger.isInfoEnabled()) {
						logger.info("No samples from:" + cntry + " in " + sheetName);
					}
					noSampleCountries.add(cntry);
					countryTaskRequired = true;
				}
			}
			ListOfValuesConstraint allowedCountries = (ListOfValuesConstraint) dictionaryService
					.getConstraint(CGGHContentModel.CONSTRAINT_COUNTRIES).getConstraint();

			ArrayList<String> missingCountries = new ArrayList<String>();
			ArrayList<String> invalidCountries = new ArrayList<String>();
			for (String cntry : sampleCountries) {
				if (!countryProps.contains(cntry)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Missing country:" + cntry + " in " + sheetName);
					}
					if (allowedCountries.getAllowedValues().contains(cntry)) {
						countryProps.add(cntry);
						countryTaskRequired = true;
						missingCountries.add(cntry);
					} else {
						if (logger.isInfoEnabled()) {
							logger.info("Invalid country:" + cntry + " in " + sheetName);
						}
						countryTaskRequired = true;
						invalidCountries.add(cntry);
					}
					addedCountry = true;

				}
			}

			if (addedCountry && autoUpdateCountries) {
				setCountries(collabNode, countryProps);
			}

			if (countryTaskRequired && !isTaskActive(collabNode, countryCheckTask)) {
				startCountryWorkflowTask(collabNode, missingCountries, noSampleCountries, invalidCountries);
			}

			boolean dateTaskRequired = false;
			if (firstDate != null && firstDate.before(getFirstExpected(collabNode))) {
				if (logger.isInfoEnabled()) {
					logger.info("First date earlier than expected:" + firstDate + " not expected until "
							+ getFirstExpected(collabNode) + " in " + sheetName);
				}
				dateTaskRequired = true;
			}

			if (lastDate != null && lastDate.after(getLastExpected(collabNode))) {
				if (logger.isInfoEnabled()) {
					logger.info("last date later than expected:" + lastDate + " not expected after "
							+ getLastExpected(collabNode) + " in " + sheetName);
				}
				dateTaskRequired = true;
			}

			if (dateTaskRequired && !isTaskActive(collabNode, dateCheckTask)) {
				startDateWorkflowTask(collabNode, firstDate, lastDate);
			}

			for (int i = 0; i < reportSheet.getNumMergedRegions(); i++) {
				CellRangeAddress region = reportSheet.getMergedRegion(i);
				resultSheet.addMergedRegion(region);
				/*
				 * if (logger.isDebugEnabled()) { logger.debug("Merged Region:"
				 * + region.formatAsString()); }
				 */
			}

			for (short column = 0; column < maxCols; column++) {
				HSSFCellStyle colStyle = reportSheet.getColumnStyle(column);
				if (colStyle != null) {
					HSSFCellStyle newCellStyle = xlswb.createCellStyle();
					// Copy style from old cell and apply to new cell
					newCellStyle.cloneStyleFrom(colStyle);

					resultSheet.setDefaultColumnStyle(column, newCellStyle);
				}
				resultSheet.autoSizeColumn(column);
				// resultSheet.setColumnWidth(column,
				// reportSheet.getColumnWidth(column));
			}

			PropertyMap properties = new PropertyMap(3);
			properties.put(ContentModel.PROP_NAME, collabReportName);
			NodeRef newFile = nodeService.getChildByName(collabNode, ContentModel.ASSOC_CONTAINS, collabReportName);
			if (newFile == null) {
				newFile = nodeService.createNode(collabNode, ContentModel.ASSOC_CONTAINS,
						QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, collabReportName),
						ContentModel.TYPE_CONTENT, properties).getChildRef();
			}
			ContentWriter cw = contentService.getWriter(newFile, ContentModel.PROP_CONTENT, true);
			cw.setMimetype(MimetypeMap.MIMETYPE_EXCEL);
			WritableByteChannel wc = cw.getWritableChannel();

			xlswb.write(Channels.newOutputStream(wc));
			wc.close();

			properties.clear();
			properties.put(ContentModel.PROP_TITLE, collabReportName);
			nodeService.addAspect(newFile, ContentModel.ASPECT_TITLED, properties);

			if (logger.isDebugEnabled()) {
				logger.debug("Created " + collabReportName + " from " + sheetName);
			}

		}

	}

	private void startStatusWorkflowTask(NodeRef collabNode) {

		String description = "Collaboration is not yet active.";
		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode, description);

		WorkflowPath path = workflowService.startWorkflow(statusCheckTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);

	}

	private void startDateWorkflowTask(NodeRef collabNode, Date firstDate, Date lastDate) {
		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode,
				"Samples processed outside expected date range");

		if (firstDate != null) {
			params.put(SSRWorkflowModel.PROP_DATE_FIRST_SAMPLE, firstDate);
		}
		if (lastDate != null) {
			params.put(SSRWorkflowModel.PROP_DATE_LAST_SAMPLE, lastDate);
		}

		WorkflowPath path = workflowService.startWorkflow(dateCheckTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);
	}

	private void startCountryWorkflowTask(NodeRef collabNode, ArrayList<String> missingCountries,
			ArrayList<String> noSampleCountries, ArrayList<String> invalidCountries) {
		Map<QName, Serializable> params = createCommonWorkflowParams(collabNode, "Countries mismatch");

		params.put(SSRWorkflowModel.PROP_COUNTRIES_MISSING, missingCountries);
		params.put(SSRWorkflowModel.PROP_COUNTRIES_NO_SAMPLES, noSampleCountries);
		params.put(SSRWorkflowModel.PROP_COUNTRIES_INVALID, noSampleCountries);

		WorkflowPath path = workflowService.startWorkflow(countryCheckTask, params);
		String instanceId = path.getInstance().getId();

		@SuppressWarnings("unused")
		WorkflowTask startTask = workflowService.getStartTask(instanceId);

	}

	private boolean isTaskActive(NodeRef collabNode, String taskName) {
		boolean active = true;
		List<WorkflowInstance> nodeTasks = workflowService.getWorkflowsForContent(collabNode, active);
		boolean found = false;
		for (WorkflowInstance wfi : nodeTasks) {
			WorkflowDefinition defn = wfi.getDefinition();
			if (defn.getId().equals(taskName)) {
				found = true;
			}
		}
		return (found);
	}

	private void setSequenced(NodeRef collabNode, int sangerYes) {
		nodeService.setProperty(collabNode, CGGHContentModel.PROP_SAMPLES_SEQUENCED, sangerYes);

	}
	private void setProcessed(NodeRef collabNode, int sangerTotal) {
		nodeService.setProperty(collabNode, CGGHContentModel.PROP_SAMPLES_PROCESSED, sangerTotal);

	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void setWorkflowService(WorkflowService workflowService) {
		this.workflowService = workflowService;
	}

	public void setAuthorityService(AuthorityService authorityService) {
		this.authorityService = authorityService;
	}

	public void setDictionaryService(DictionaryService dictService) {
		this.dictionaryService = dictService;
	}

	protected File resave(ContentReader reader) throws Exception {
		reader.setMimetype(MimetypeMap.MIMETYPE_EXCEL);

		File xlsTargetFile = TempFileProvider.createTempFile("-target-", ".xls");
		ContentWriter writer = new FileContentWriter(xlsTargetFile);
		writer.setMimetype(MimetypeMap.MIMETYPE_EXCEL);

		transformer.transform(reader, writer, null);

		return xlsTargetFile;
	}

	@Override
	protected void executeImpl(Action action, NodeRef actionedUponNodeRef) {

		setTransform(action);
		setNameCheckGroup(action);
		setNameCheckTask(action);
		setCountCheckTask(action);
		setStatusCheckTask(action);
		setCountryCheckTask(action);
		setCollabDocTask(action);
		setDateCheckTask(action);

		ContentReader reader = contentService.getReader(actionedUponNodeRef, ContentModel.PROP_CONTENT);
		File tmpFile = null;
		try {
			if (transform) {
				tmpFile = resave(reader);
				reader = new FileContentReader(tmpFile);
			}
			process(reader.getContentInputStream());

			action.setParameterValue(PARAM_RESULT, "");
		} catch (ContentIOException e) {
			logger.error("", e);
			action.setParameterValue(PARAM_RESULT, "Action failed." + e.getMessage());
		} catch (IOException e) {
			logger.error("", e);
			action.setParameterValue(PARAM_RESULT, "Action failed." + e.getMessage());
		} catch (Exception e) {
			logger.error("", e);
			action.setParameterValue(PARAM_RESULT, "Action failed." + e.getMessage());
		} finally {
			if (tmpFile != null) {
				tmpFile.delete();
			}
		}
	}

	@Override
	protected void addParameterDefinitions(List<ParameterDefinition> paramList) {
		// Add definitions for action parameters
		paramList.add(new ParameterDefinitionImpl(PARAM_TRANSFORM, DataTypeDefinition.BOOLEAN, false,
				getParamDisplayLabel(PARAM_TRANSFORM)));
		paramList.add(new ParameterDefinitionImpl(PARAM_TASK_GROUP, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_TASK_GROUP)));
		paramList.add(new ParameterDefinitionImpl(PARAM_NAMECHECKTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_NAMECHECKTASK)));

		paramList.add(new ParameterDefinitionImpl(PARAM_COUNTCHECKTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_COUNTCHECKTASK)));

		paramList.add(new ParameterDefinitionImpl(PARAM_STATUSCHECKTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_STATUSCHECKTASK)));

		paramList.add(new ParameterDefinitionImpl(PARAM_COUNTRYCHECKTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_COUNTRYCHECKTASK)));

		paramList.add(new ParameterDefinitionImpl(PARAM_DATECHECKTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_DATECHECKTASK)));

		paramList.add(new ParameterDefinitionImpl(PARAM_COLLABDOCTASK, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_COLLABDOCTASK)));
		
		paramList.add(new ParameterDefinitionImpl(PARAM_RESULT, DataTypeDefinition.TEXT, false,
				getParamDisplayLabel(PARAM_RESULT)));

	}

	public void setTransformer(ContentTransformerWorker transformer) {
		this.transformer = transformer;
	}

	public void setTransform(Action action) {
		Boolean t = (Boolean) action.getParameterValue(PARAM_TRANSFORM);
		if (t == null) {
			transform = DEFAULT_TRANSFORM;
		} else {
			transform = t;
		}
	}

	public void setNameCheckTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_NAMECHECKTASK);
		if (t == null) {
			nameCheckTask = DEFAULT_NAMECHECK_TASK;
		} else {
			nameCheckTask = t;
		}
	}

	public void setCountCheckTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_COUNTCHECKTASK);
		if (t == null) {
			countCheckTask = DEFAULT_COUNTCHECK_TASK;
		} else {
			countCheckTask = t;
		}
	}

	public void setDateCheckTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_DATECHECKTASK);
		if (t == null) {
			dateCheckTask = DEFAULT_DATECHECKTASK;
		} else {
			dateCheckTask = t;
		}
	}

	public void setCountryCheckTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_COUNTRYCHECKTASK);
		if (t == null) {
			countryCheckTask = DEFAULT_COUNTRYCHECKTASK;
		} else {
			countryCheckTask = t;
		}
	}

	public void setStatusCheckTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_STATUSCHECKTASK);
		if (t == null) {
			statusCheckTask = DEFAULT_STATUSCHECKTASK;
		} else {
			statusCheckTask = t;
		}
	}

	public void setCollabDocTask(Action action) {
		String t = (String) action.getParameterValue(PARAM_COLLABDOCTASK);
		if (t == null) {
			collabDocTask = DEFAULT_COLLABDOCTASK;
		} else {
			collabDocTask = t;
		}
	}
	
	public void setNameCheckGroup(Action action) {
		String t = (String) action.getParameterValue(PARAM_TASK_GROUP);
		if (t == null) {
			nameCheckGroup = DEFAULT_NAMECHECK_GROUP;
		} else {
			nameCheckGroup = t;
		}
	}

}
