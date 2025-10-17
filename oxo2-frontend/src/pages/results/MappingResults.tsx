import {useMemo, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {Search} from "../../components/search/Search";
import {SearchInput} from "../../model/Search";
import {emptyFacetedMapping, FacetedMapping, fetchMappings, fromJson} from "./MappingResultsSlice";
import {useQuery} from "@tanstack/react-query";
import {MaterialReactTable, type MRT_ColumnDef, MRT_PaginationState, useMaterialReactTable} from 'material-react-table';
import {Mapping} from "../../model/Mapping.ts";
import { ThemeProvider, createTheme } from '@mui/material/styles';

function MappingResults() {
    const navigate = useNavigate();

    const [pagination, setPagination] = useState<MRT_PaginationState>({
        pageIndex: 0,
        pageSize: 10,
    });
    const { curies } = useParams<{ curies: string }>();
    const searchInput: SearchInput = {
        userSearchInput: curies || "",
        sanitizedSearchInput: curies
            ? curies.split(/[\n,]+/).filter((item) => item.trim() !== "")
            : [],
    };

    const [columnFilters, setColumnFilters] = useState<any[]>([]);
    const [sorting, setSorting] = useState<any[]>([
        { id: 'subject_id', desc: false }
    ]);

    const { data, isLoading, isError } = useQuery({
        queryKey: [
            "fetchMappings",
            searchInput.sanitizedSearchInput,
            pagination.pageIndex,
            pagination.pageSize,
            columnFilters,
            sorting
        ],
        queryFn: () =>
            fetchMappings(
                searchInput.sanitizedSearchInput,
                pagination.pageIndex,
                pagination.pageSize,
                columnFilters,
                sorting
            ),
        staleTime: Infinity,
    });

    const mappingResults: FacetedMapping = data ? fromJson(data) : emptyFacetedMapping;

    const columns = useMemo<MRT_ColumnDef<Mapping>[]>(
        () => [
            {
                accessorKey: "subjectId",
                header: "Subject Id",
                // enableHiding: false
            },
            {
                accessorKey: "subjectIri",
                header: "Subject IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "subjectLabel",
                header: "Subject Label",
            },
            {
                accessorKey: "predicateId",
                header: "Predicate Id",
                // enableHiding: false
            },
            {
                accessorKey: "predicateIri",
                header: "Predicate IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "objectId",
                header: "Object Id",
                // enableHiding: false
            },
            {
                accessorKey: "objectIri",
                header: "Object IRI",
                Cell: ({ cell }) => (
                    <div style={{ wordBreak: "break-all", whiteSpace: "pre-wrap" }}>
                        {cell.getValue<string>()}
                    </div>
                ),
            },
            {
                accessorKey: "objectLabel",
                header: "Object Label",
            },
            {
                accessorKey: "mappingJustification",
                header: "Mapping Justification",

            },
            {
                accessorKey: "mappingProvider",
                header: "Mapping Provider"
            },
            {
                accessorKey: "mappingSetId",
                header: "Mapping Set Id"
            },
            {
                accessorKey: "mappingTool",
                header: "Mapping Tool",
            },
            {
                accessorKey: "license",
                header: "License",
            }

        ],
        []
    );

    const [columnVisibility, setColumnVisibility] = useState({

        license: false,
        mappingJustification: false,
        mappingProvider: false,
        mappingSetId: false,
        mappingTool: false,
        objectIri: true,
        predicateIri: true,
        subjectIri: true
        }
    )

    const table = useMaterialReactTable({
        columns,
        data: mappingResults.mappings,
        enableColumnOrdering: true,
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: true,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        manualPagination: true, //turn off built-in client-side pagination
        manualFiltering: true, // Enable manual filtering
        manualSorting: true,
        // enableMultiSort: true,
        muiTablePaperProps: {
            sx: {
                '& .MuiBox-root:has(.MuiTablePagination-root)': {
                    justifyContent: 'center',
                    width: '100%',
                    display: 'flex'
                }
            },
        },
        //give loading spinner somewhere to go while loading
        muiTableBodyProps: {
            children: isLoading ? (
                <tr style={{ height: '200px' }}>
                    <td />
                </tr>
            ) : undefined,
            sx: {
                //stripe the rows, make odd rows a darker color
                '& tr:nth-of-type(even) > td': {
                    backgroundColor: '#F3F4F6',
                },
            },
        },
        muiTableHeadCellProps: {
            //simple styling with the `sx` prop, works just like a style prop in this example
            sx: {
                fontWeight: 'bold',
                fontSize: '16px',
            },
        },
        muiToolbarAlertBannerProps: isError
            ? {
                color: 'error',
                children: 'Error loading data',
            }
            : undefined,
        paginationDisplayMode: 'pages',
        muiPaginationProps: {
            rowsPerPageOptions: [10, 20, 30, 50],
            boundaryCount: 1,
            color: 'primary',
            size: 'medium',
            siblingCount: 1,
        },
        onPaginationChange: setPagination,
        onColumnFiltersChange: setColumnFilters,
        onSortingChange: setSorting,
        rowCount: (mappingResults?.totalElements) ?? 0,
        state: {
            isLoading,
            pagination,
            columnFilters, // Pass filter state to table
            showAlertBanner: isError,
            columnVisibility,
            sorting
        },
        initialState: {
            showColumnFilters: true
        },
        onColumnVisibilityChange: setColumnVisibility,
        enableHiding: true,
        enableTopToolbar: true, // Show toolbar so user can access column visibility menu
        muiTableBodyRowProps: ({ row }) => ({
            onClick: () => {
                const mapping = row.original;
                navigate(`/mapping/${mapping.mappingId}`, { state: { mapping } });
            },
            style: { cursor: 'pointer' }
        })
    });

    const tableTheme = createTheme({
        palette: {
            primary: {
                main: '#d4522c',
                light: '#b75c00',
                dark: '#461901',
                contrastText: '#fff'
            },
            secondary: {
                main: '#525252',
                light: '#99a1af',
                dark: '#373a36',
                contrastText: '#fff'
            },
            // You can add more palette customization here
        },
    });

    return (
        <div>
            <Search searchInput={searchInput} />

            {isLoading && (
                <div className="flex justify-center p-8">
                    <div className="spinner-default w-10 h-10 animate-spin" role="status">
                        <span className="sr-only">Loading...</span>
                    </div>
                </div>
            )}
            <ThemeProvider theme={tableTheme}>
                <MaterialReactTable table={table}/>
            </ThemeProvider>
        </div>
    );
}

export default MappingResults;
