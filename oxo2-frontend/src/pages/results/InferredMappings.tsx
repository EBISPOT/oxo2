import {useMemo, useState} from "react";
import {MaterialReactTable, MRT_ColumnDef, useMaterialReactTable} from "material-react-table";
import {InferredMapping, Mapping} from "../../model/Mapping.ts";

function InferredMappings({ mapping }: { mapping: Mapping }) {
    const assertedMappingColumns = useMemo<MRT_ColumnDef<InferredMapping>[]>(
        () => [
            {
                accessorKey: "mappingId",
                header: "Mapping Id"
            },
            {
                accessorKey: "subjectId",
                header: "Subject Id",
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
            // {
            //     accessorKey: "mappingJustification",
            //     header: "Mapping Justification",
            // },
        ],
        []
    );

    const [columnFilters, setColumnFilters] = useState<any[]>([]);
    const [columnVisibility] = useState({ mappingId: false })

    const assertedMappingsTable = useMaterialReactTable({
        columns: assertedMappingColumns,
        data: mapping.assertedMappings || [],
        onColumnFiltersChange: setColumnFilters,
        state: {
            columnFilters,
            columnVisibility
        },
        enableFilterMatchHighlighting: true,
        enableGlobalFilter: false,
        enableFullScreenToggle: false,
        enableDensityToggle: false,
        enableHiding: false,
        enableTopToolbar: false,
        enablePagination: false,
    });

    return (
        <div>
            <h1 className="section-subheading">Asserted Mappings</h1>
            <MaterialReactTable table={assertedMappingsTable}/>
        </div>
    );
}
export default InferredMappings;